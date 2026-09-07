#!/usr/bin/env python3

import base64
import unittest
from unittest.mock import Mock, patch
from pathlib import Path

from scripts import work_graph_guard as guard


ROOT = Path(__file__).resolve().parents[1]
REQUIRED_PROTECTED_ASSETS = {
    ".github/workflows/work-graph-guard.yml",
    ".github/workflows/work-graph-guard-tests.yml",
    "scripts/test_work_graph_guard.py",
}


class ReviewFreshnessTests(unittest.TestCase):
    def test_current_head_codex_review_is_accepted(self):
        head = "a" * 40
        reviews = [
            {
                "commit_id": head,
                "user": {"id": guard.CODEX_USER_ID, "login": "chatgpt-codex-connector[bot]"},
            }
        ]
        self.assertTrue(guard.has_current_head_codex_review(reviews, head))

    def test_superseded_codex_review_is_rejected(self):
        reviews = [
            {
                "commit_id": "a" * 40,
                "user": {"id": guard.CODEX_USER_ID, "login": "chatgpt-codex-connector[bot]"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "b" * 40))

    def test_prefix_collision_reviewer_is_rejected(self):
        reviews = [
            {
                "commit_id": "a" * 40,
                "user": {"id": 123456, "login": "chatgpt-codex-connector-fake"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "a" * 40))

    def test_human_review_on_current_head_does_not_substitute_for_codex(self):
        reviews = [
            {
                "commit_id": "a" * 40,
                "user": {"id": 9963055, "login": "repository-owner"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "a" * 40))

    def test_clean_codex_comment_on_current_head_is_accepted(self):
        head = "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
        comments = [{
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": guard.CODEX_APP_ID},
            "body": f"Codex Review: Didn't find any major issues. :rocket:\n\n**Reviewed commit:** `{head}`",
        }]
        self.assertTrue(guard.has_current_head_clean_codex_result(comments, head))

    def test_clean_codex_comment_on_superseded_head_is_rejected(self):
        comments = [{
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": guard.CODEX_APP_ID},
            "body": "Codex Review: Didn't find any major issues. :rocket:\n\n**Reviewed commit:** `3f8fc1e6e80d0449e548795dc66154aa18f3815d`",
        }]
        self.assertFalse(
            guard.has_current_head_clean_codex_result(
                comments, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            )
        )

    def test_clean_result_from_wrong_app_is_rejected(self):
        comments = [{
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": 1},
            "body": "Codex Review: Didn't find any major issues. :rocket:\n\n**Reviewed commit:** `3f8fc1e6e80d0449e548795dc66154aa18f3815d`",
        }]
        self.assertFalse(
            guard.has_current_head_clean_codex_result(
                comments, "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
            )
        )

    def test_short_reviewed_commit_prefix_is_rejected(self):
        comment = {
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": guard.CODEX_APP_ID},
            "body": "Codex Review: Didn't find any major issues.\n**Reviewed commit:** `3f8fc1e6e8`",
        }
        self.assertFalse(guard.has_current_head_clean_codex_result(
            [comment], "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
        ))

    def test_dismissed_exact_head_review_is_rejected(self):
        review = {"commit_id": "a" * 40, "state": "DISMISSED", "user": {"id": guard.CODEX_USER_ID}}
        self.assertFalse(guard.has_current_head_codex_review([review], "a" * 40))


class ProtectedGitObjectTests(unittest.TestCase):
    HEAD = "a" * 40
    ROOT_TREE = "b" * 40
    SCRIPTS_TREE = "c" * 40
    GUARD_BLOB = "d" * 40

    @classmethod
    def responses(cls, content=b"guard", leaf_mode="100644"):
        return [
            {"sha": cls.HEAD, "tree": {"sha": cls.ROOT_TREE}},
            {
                "truncated": False,
                "tree": [{
                    "path": "scripts", "type": "tree", "mode": "040000",
                    "sha": cls.SCRIPTS_TREE,
                }],
            },
            {
                "truncated": False,
                "tree": [{
                    "path": "work_graph_guard.py", "type": "blob",
                    "mode": leaf_mode, "sha": cls.GUARD_BLOB,
                }],
            },
            {
                "sha": cls.GUARD_BLOB,
                "encoding": "base64",
                "size": len(content),
                "content": base64.b64encode(content).decode("ascii"),
            },
        ]

    def test_regular_guard_blob_is_read_from_immutable_git_objects(self):
        calls = []
        responses = iter(self.responses(content=b"trusted"))

        def fake_api(path):
            calls.append(path)
            return next(responses)

        with patch.object(guard, "api", side_effect=fake_api):
            self.assertEqual(
                "trusted", guard.read_regular_git_blob(self.HEAD, guard.GUARD_SOURCE)
            )
        self.assertEqual(
            [
                f"/repos/{guard.REPO}/git/commits/{self.HEAD}",
                f"/repos/{guard.REPO}/git/trees/{self.ROOT_TREE}",
                f"/repos/{guard.REPO}/git/trees/{self.SCRIPTS_TREE}",
                f"/repos/{guard.REPO}/git/blobs/{self.GUARD_BLOB}",
            ],
            calls,
        )
        self.assertFalse(any("/contents/" in call for call in calls))

    def test_non_regular_guard_modes_are_rejected_before_blob_read(self):
        for mode, object_type in (
            ("100755", "blob"),
            ("120000", "blob"),
            ("160000", "commit"),
            ("040000", "tree"),
        ):
            with self.subTest(mode=mode):
                responses = self.responses(leaf_mode=mode)
                responses[2]["tree"][0]["type"] = object_type
                api_mock = Mock(side_effect=responses)
                with patch.object(guard, "api", api_mock):
                    with self.assertRaisesRegex(RuntimeError, "must be 100644/blob"):
                        guard.read_regular_git_blob(self.HEAD, guard.GUARD_SOURCE)
                self.assertEqual(3, api_mock.call_count)

    def test_tree_component_integrity_fails_closed(self):
        variants = (
            {"truncated": True, "tree": []},
            {"truncated": False, "tree": []},
            {
                "truncated": False,
                "tree": [
                    {"path": "scripts", "type": "tree", "mode": "040000", "sha": self.SCRIPTS_TREE},
                    {"path": "scripts", "type": "tree", "mode": "040000", "sha": self.SCRIPTS_TREE},
                ],
            },
            {
                "truncated": False,
                "tree": [{"path": "scripts", "type": "blob", "mode": "100644", "sha": self.SCRIPTS_TREE}],
            },
        )
        for root_tree in variants:
            with self.subTest(root_tree=root_tree):
                responses = self.responses()
                responses[1] = root_tree
                with patch.object(guard, "api", side_effect=responses):
                    with self.assertRaises(RuntimeError):
                        guard.read_regular_git_blob(self.HEAD, guard.GUARD_SOURCE)

    def test_candidate_commit_identity_and_tree_sha_are_strict(self):
        for head, commit in (
            ("not-a-sha", {}),
            (self.HEAD, {"sha": "e" * 40, "tree": {"sha": self.ROOT_TREE}}),
            (self.HEAD, {"sha": self.HEAD, "tree": {"sha": "invalid"}}),
        ):
            with self.subTest(head=head, commit=commit):
                with patch.object(guard, "api", return_value=commit):
                    with self.assertRaises(RuntimeError):
                        guard.read_regular_git_blob(head, guard.GUARD_SOURCE)

    def test_blob_identity_encoding_size_and_utf8_are_strict(self):
        invalid_blobs = (
            {"sha": "e" * 40, "encoding": "base64", "size": 5, "content": "Z3VhcmQ="},
            {"sha": self.GUARD_BLOB, "encoding": "utf-8", "size": 5, "content": "guard"},
            {"sha": self.GUARD_BLOB, "encoding": "base64", "size": 1, "content": "%%%"},
            {"sha": self.GUARD_BLOB, "encoding": "base64", "size": 6, "content": "Z3VhcmQ="},
            {"sha": self.GUARD_BLOB, "encoding": "base64", "size": 1, "content": "/w=="},
        )
        for blob in invalid_blobs:
            with self.subTest(blob=blob):
                responses = self.responses()
                responses[3] = blob
                with patch.object(guard, "api", side_effect=responses):
                    with self.assertRaises(RuntimeError):
                        guard.read_regular_git_blob(self.HEAD, guard.GUARD_SOURCE)


class MainIntegrationTests(unittest.TestCase):
    def test_exact_clean_comment_requires_resolved_threads(self):
        head = "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
        comment = {
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": guard.CODEX_APP_ID},
            "body": f"Codex Review: Didn't find any major issues.\n**Reviewed commit:** `{head}`",
        }
        with (
            patch.object(guard, "pages", side_effect=(iter(()), iter((comment,)))),
            patch.object(guard, "review_threads_resolved", return_value=True),
        ):
            self.assertTrue(guard.has_exact_head_codex_evidence(326, head))
        with (
            patch.object(guard, "pages", side_effect=(iter(()), iter((comment,)))),
            patch.object(guard, "review_threads_resolved", return_value=False),
        ):
            self.assertFalse(guard.has_exact_head_codex_evidence(326, head))

    def test_review_thread_query_fails_closed_on_unresolved_or_paginated(self):
        base = {"data": {"repository": {"pullRequest": {"reviewThreads": {
            "nodes": [{"isResolved": False}], "pageInfo": {"hasNextPage": False}
        }}}}}
        with patch.object(guard, "api_request", return_value=base):
            self.assertFalse(guard.review_threads_resolved(326))
        base["data"]["repository"]["pullRequest"]["reviewThreads"]["pageInfo"]["hasNextPage"] = True
        with patch.object(guard, "api_request", return_value=base):
            with self.assertRaises(RuntimeError):
                guard.review_threads_resolved(326)

    def test_trusted_event_target_requires_exact_base_and_same_repository(self):
        event = {"number": 42, "pull_request": {"number": 42,
            "head": {"sha": "a" * 40, "repo": {"full_name": guard.REPO}},
            "base": {"sha": "b" * 40, "ref": "main"}}}
        with patch.multiple(guard, EVENT_NAME="pull_request_target", WORKFLOW_SHA="b" * 40):
            self.assertEqual({"number": 42, "sha": "a" * 40}, guard.trusted_event_target(event))
            event["pull_request"]["head"]["repo"]["full_name"] = "fork/repo"
            self.assertIsNone(guard.trusted_event_target(event))

    def test_main_publishes_both_pending_contexts_before_metadata_reads(self):
        target = {"number": 42, "sha": "a" * 40, "same_repo": True}
        calls = []
        def publish(item, context, state, _description):
            calls.append((item["sha"], context, state))
        def active():
            self.assertEqual([guard.REVIEW_STATUS_CONTEXT, guard.INTEGRITY_STATUS_CONTEXT],
                             [item[1] for item in calls[:2]])
            return [target]
        with (
            patch.object(guard, "load_event_payload", return_value={}),
            patch.object(guard, "trusted_event_target", return_value=target),
            patch.object(guard, "active_main_prs", side_effect=active),
            patch.object(guard, "pages", return_value=iter(())),
            patch.object(guard, "require_durable_workflow_surface"),
            patch.object(guard, "has_exact_head_codex_evidence", return_value=True),
            patch.object(guard, "snapshot_is_current_and_unique", return_value=True),
            patch.object(guard, "publish_status", side_effect=publish),
        ):
            self.assertEqual(0, guard.main())
        self.assertTrue(any(context == guard.REVIEW_STATUS_CONTEXT and state == "success"
                            for _, context, state in calls))
        self.assertTrue(any(context == guard.INTEGRITY_STATUS_CONTEXT and state == "success"
                            for _, context, state in calls))

    def test_exception_after_pending_invalidates_both_contexts(self):
        target = {"number": 42, "sha": "a" * 40, "same_repo": True}
        calls = []
        with (
            patch.object(guard, "load_event_payload", return_value={}),
            patch.object(guard, "trusted_event_target", return_value=target),
            patch.object(guard, "active_main_prs", side_effect=RuntimeError("API down")),
            patch.object(guard, "publish_status", side_effect=lambda item, context, state, _: calls.append((context, state))),
        ):
            self.assertEqual(1, guard.main())
        self.assertIn((guard.REVIEW_STATUS_CONTEXT, "pending"), calls)
        self.assertIn((guard.INTEGRITY_STATUS_CONTEXT, "pending"), calls)
        self.assertIn((guard.REVIEW_STATUS_CONTEXT, "failure"), calls)
        self.assertIn((guard.INTEGRITY_STATUS_CONTEXT, "failure"), calls)

    def test_fork_and_shared_heads_never_receive_success(self):
        shared = "a" * 40
        targets = [
            {"number": 1, "sha": shared, "same_repo": False},
            {"number": 2, "sha": shared, "same_repo": True},
        ]
        with patch.object(guard, "active_main_prs", return_value=targets):
            self.assertFalse(guard.snapshot_is_current_and_unique([targets[1]]))
        calls = []
        with (
            patch.object(guard, "load_event_payload", return_value={}),
            patch.object(guard, "trusted_event_target", return_value=None),
            patch.object(guard, "active_main_prs", return_value=targets),
            patch.object(guard, "pages", return_value=iter(())),
            patch.object(guard, "require_durable_workflow_surface"),
            patch.object(guard, "has_exact_head_codex_evidence", return_value=True),
            patch.object(guard, "snapshot_is_current_and_unique", return_value=False),
            patch.object(guard, "publish_status", side_effect=lambda item, context, state, _: calls.append(state)),
        ):
            self.assertEqual(1, guard.main())
        self.assertNotIn("success", calls)

    def test_real_durable_surface_guard_rejects_deleted_protected_workflows(self):
        pull_request = {
            "state": "open",
            "draft": False,
            "base": {"ref": "main"},
            "head": {"sha": "b" * 40},
        }
        manifest_payload = {
            "type": "file",
            "encoding": "base64",
            "content": base64.b64encode(b"").decode("ascii"),
        }

        def fake_api(path):
            if path == "/repos/jdoe-dev-159753/specgraph-reference-app/pulls/308":
                return pull_request
            if "/contents/scripts/ci/durable-workflows.txt?" in path:
                return manifest_payload
            if "/git/trees/" in path:
                return {"truncated": False, "tree": []}
            if "/contents/scripts/test_work_graph_guard.py?" in path:
                return {
                    "type": "file",
                    "encoding": "base64",
                    "content": base64.b64encode(
                        Path(__file__).read_bytes() + b"\n"
                    ).decode("ascii"),
                }
            if "/contents/scripts/work_graph_guard.py?" in path:
                return {
                    "type": "file",
                    "encoding": "base64",
                    "content": base64.b64encode(Path(guard.__file__).read_bytes()).decode("ascii"),
                }
            if "/contents/.github/scripts/project-v2-reconcile.cjs?" in path:
                return {
                    "type": "file", "encoding": "base64",
                    "content": base64.b64encode(
                        (Path(__file__).resolve().parents[1] / ".github/scripts/project-v2-reconcile.cjs").read_bytes()
                    ).decode("ascii"),
                }
            raise AssertionError(f"unexpected API path: {path}")

        with (
            patch.object(guard, "api", side_effect=fake_api),
            patch.object(
                guard, "read_regular_git_blob",
                return_value=Path(guard.__file__).read_bytes().decode("utf-8"),
            ),
        ):
            failures = []
            guard.require_durable_workflow_surface(308, failures)

        self.assertTrue(
            any("work-graph-guard.yml: protected asset is missing" in item for item in failures)
        )
        self.assertTrue(
            any("work-graph-guard-tests.yml: protected asset is missing" in item for item in failures)
        )
        self.assertTrue(
            any("scripts/test_work_graph_guard.py: protected asset changed" in item for item in failures)
        )

    def test_truncated_recursive_tree_fails_closed_without_files_listing(self):
        pr = {"state": "open", "draft": False, "base": {"ref": "main"}, "head": {"sha": "c" * 40}}
        manifest = {"type": "file", "encoding": "base64", "content": ""}
        with (
            patch.object(guard, "api", side_effect=[pr, manifest, {"truncated": True, "tree": []}]),
            patch.object(
                guard, "read_regular_git_blob",
                return_value=Path(guard.__file__).read_bytes().decode("utf-8"),
            ),
        ):
            failures = []
            guard.require_durable_workflow_surface(309, failures)
        self.assertEqual(["pull request #309: recursive Git tree is missing or truncated"], failures)

    def test_every_open_main_pr_gets_full_tree_and_guard_source_audit(self):
        pull_request = {"state": "open", "draft": False, "base": {"ref": "main"}, "head": {"sha": "d" * 40}}
        payload = {"type": "file", "encoding": "base64", "content": base64.b64encode(b"placeholder").decode("ascii")}
        calls = []
        def fake_api(path):
            calls.append(path)
            self.assertNotIn("/files", path)
            if path.endswith("/pulls/310"):
                return pull_request
            if "/git/trees/" in path:
                return {"truncated": False, "tree": [{"type": "blob", "path": ".github/workflows/proof.yml"}]}
            return payload
        patches = (patch.object(guard, "api", side_effect=fake_api),
                   patch.object(guard, "read_regular_git_blob", return_value="placeholder"),
                   patch.object(guard, "workflow_inventory_violations", return_value=[]),
                   patch.object(guard, "protected_guard_source_violations", return_value=[]),
                   patch.dict(guard.PROTECTED_ASSET_SHA256, {}, clear=True))
        with patches[0], patches[1] as git_blob, patches[2], patches[3], patches[4]:
            failures = []
            guard.require_durable_workflow_surface(310, failures)
        self.assertEqual([], failures)
        self.assertTrue(any("/git/trees/" in call for call in calls))
        git_blob.assert_called_once_with("d" * 40, guard.GUARD_SOURCE)

    def test_target_only_change_cannot_bypass_a_symlinked_guard(self):
        head = "e" * 40
        pull_request = {
            "state": "open", "draft": False, "base": {"ref": "main"},
            "head": {"sha": head},
        }
        root_tree = "f" * 40
        scripts_tree = "1" * 40
        calls = []

        def fake_api(path):
            calls.append(path)
            if path.endswith("/pulls/311"):
                return pull_request
            if path.endswith(f"/git/commits/{head}"):
                return {"sha": head, "tree": {"sha": root_tree}}
            if path.endswith(f"/git/trees/{root_tree}"):
                return {"truncated": False, "tree": [{
                    "path": "scripts", "type": "tree", "mode": "040000", "sha": scripts_tree,
                }]}
            if path.endswith(f"/git/trees/{scripts_tree}"):
                return {"truncated": False, "tree": [{
                    "path": "work_graph_guard.py", "type": "blob", "mode": "120000",
                    "sha": "2" * 40,
                }]}
            raise AssertionError(f"unexpected API path: {path}")

        with (
            patch.object(guard, "api", side_effect=fake_api),
            patch.object(
                guard, "pages",
                return_value=iter(([{"filename": "scripts/guard_target.py"}],)),
            ),
        ):
            failures = []
            guard.require_durable_workflow_surface(311, failures)

        self.assertTrue(any("must be 100644/blob" in finding for finding in failures))
        self.assertFalse(any("/contents/" in call for call in calls))
        self.assertFalse(any("/git/blobs/" in call for call in calls))


class DurableWorkflowTests(unittest.TestCase):
    @staticmethod
    def valid_workflow(name="proof", trigger="workflow_dispatch"):
        condition = " && ".join((*guard.REQUIRED_JOB_CLAUSES, "github.event_name == 'workflow_dispatch'"))
        return (
            f"name: {name}\n\non:\n  {trigger}:\n\npermissions:\n  contents: read\n\n"
            f"concurrency:\n{guard.CANONICAL_QUEUE_GROUP}\n"
            "  cancel-in-progress: false\n  queue: max\n\njobs:\n  verify:\n"
            f"    if: ${{{{ {condition} }}}}\n{guard.PRIVATE_RUNNER}\n"
            "    steps:\n      - run: 'true'\n"
        )

    def test_parse_manifest_ignores_comments_and_blank_lines(self):
        manifest = "# durable\napplication-ci.yml\n\n r4-acceptance-ci.yml \n"
        self.assertEqual(
            {"application-ci.yml", "r4-acceptance-ci.yml"},
            guard.parse_durable_workflow_manifest(manifest),
        )

    def test_exact_canonical_inventory_is_accepted(self):
        workflows = {
            "proof.yml": self.valid_workflow("proof"),
            "other-proof.yml": self.valid_workflow("other-proof"),
        }
        manifest = "proof.yml\nother-proof.yml\n"
        self.assertEqual([], guard.workflow_inventory_violations(workflows, manifest))

    def test_undeclared_and_missing_workflows_are_rejected(self):
        workflows = {
            "application-ci.yml": "name: application-ci\n",
            "temporary-proof.yml": "name: temporary-proof\n",
        }
        findings = guard.workflow_inventory_violations(
            workflows, "application-ci.yml\nr4-acceptance-ci.yml\n"
        )
        self.assertTrue(any("not declared durable" in finding for finding in findings))
        self.assertTrue(any("missing from repository" in finding for finding in findings))

    def test_numbered_one_shot_filename_is_rejected(self):
        for name in ("discovery-219-fix", "192-issue", "192-pr", "192-fix", "42-discovery", "42-story", "ci-192-issue", "workflow-192-pr", "close-42-fix", "durable-42-discovery", "x-42-story"):
            findings = guard.workflow_inventory_violations({f"{name}.yml": f"name: {name}\n"}, f"{name}.yml\n")
            self.assertTrue(any("one-shot workflow identity" in finding or "workflow must start" in finding for finding in findings))

    def test_numbered_identity_markers_are_rejected(self):
        separators = ("-", "_", " ", ".", "#", ":")
        for keyword in ("pr", "prs", "pull-request", "pull requests", "issue", "issues", "discovery", "discoveries", "story", "stories", "fix", "fixes"):
            for marker in ("no", "number", "id"):
                for separator in separators:
                    workflow_name = f"{keyword}{separator}{marker}{separator}42"
                    filename = workflow_name.replace(" ", "-") + ".yml"
                    with self.subTest(workflow_name=workflow_name):
                        self.assertIsNotNone(
                            guard.ONE_SHOT_WORKFLOW.search(workflow_name)
                        )
                        findings = guard.workflow_inventory_violations(
                            {filename: f"name: {filename.rsplit('.', 1)[0]}\n"},
                            f"{filename}\n",
                        )
                        self.assertTrue(
                            any(
                                "one-shot workflow identity" in finding
                                for finding in findings
                            )
                        )

    def test_direct_separated_numeric_identities_are_rejected(self):
        for workflow_name in ("issue-42", "pr #17", "pull request:9", "fix_8"):
            with self.subTest(workflow_name=workflow_name):
                self.assertIsNotNone(guard.ONE_SHOT_WORKFLOW.search(workflow_name))

    def test_non_marker_words_glued_markers_and_incomplete_ids_remain_allowed(self):
        for workflow_name in (
            "issue-no",
            "version-42",
        ):
            with self.subTest(workflow_name=workflow_name):
                self.assertIsNone(guard.ONE_SHOT_WORKFLOW.search(workflow_name))

    def test_name_must_equal_filename_stem(self):
        findings = guard.workflow_inventory_violations(
            {"proof.yml": "name: durable-looking-name\n"}, "proof.yml\n"
        )
        self.assertTrue(any("must equal filename stem" in finding for finding in findings))

    def test_noncanonical_yaml_name_forms_fail_closed(self):
        forms = (
            "",
            "# comment\nname: proof\n",
            "  name: proof\n",
            '"name": proof\n',
            "name: \"proof\"\n",
            "name: *identity\nidentity: &identity proof\n",
            "name: >-\n  proof\n",
            'name: "pro\\\nof"\n',
            "{name: proof, on: {workflow_dispatch: {}}}\n",
        )
        for workflow in forms:
            with self.subTest(workflow=workflow):
                findings = guard.workflow_inventory_violations(
                    {"proof.yml": workflow}, "proof.yml\n"
                )
                self.assertTrue(
                    any("workflow must start with exactly" in finding for finding in findings)
                )

    def test_noncanonical_root_keys_after_name_fail_closed(self):
        workflows = (
            "name: proof\nname: issue-42-proof\n",
            'name: proof\n"na\\u006de": issue-42-proof\n',
            "name: proof\n? name\n: issue-42-proof\n",
            "name: proof\n{name: issue-42-proof}\n",
        )
        for workflow in workflows:
            with self.subTest(workflow=workflow):
                findings = guard.workflow_inventory_violations(
                    {"proof.yml": workflow}, "proof.yml\n"
                )
                self.assertTrue(
                    any(
                        "non-canonical or unknown top-level YAML key" in finding
                        for finding in findings
                    )
                )

    def test_yaml_explicit_keys_anchors_aliases_and_tags_fail_closed(self):
        workflow = self.valid_workflow()
        mutations = (
            workflow.replace("      - run: 'true'", "      - ? uses\n        : actions/checkout@v6"),
            workflow.replace("      - run: 'true'", "      - ? continue-on-error\n        : true\n        run: 'true'"),
            workflow.replace("permissions:\n", "env:\n  HIDDEN: &hidden uses\npermissions:\n").replace(
                "      - run: 'true'", "      - *hidden: actions/checkout@v6"
            ),
            workflow.replace("    steps:\n", "    strategy: &wide\n      matrix:\n        item: [a, b]\n    steps:\n").replace(
                "    steps:\n", "    strategy: *wide\n    steps:\n"
            ),
            *(workflow.replace("      - run: 'true'", f"      - {tag} {{run: 'true'}}") for tag in
              ("!unsafe", "!1", "!-", "!<tag:yaml.org,2002:map>", "!", "!(", "!=")),
            workflow.replace("      - run: 'true'", "      - run: |\n          '\n      - ? continue-on-error\n        : true\n        run: exit 1"),
            *(workflow.replace("permissions:\n", f"env:\n  HIDDEN: &{anchor} uses\npermissions:\n").replace(
                "      - run: 'true'", f"      - *{anchor}: actions/checkout@v6"
            ) for anchor in ("1", "-", ".", ">", "*", "&")),
            *(workflow.replace("      - run: 'true'", f"      - name: tagged\n        run: {tag} true") for tag in ("!unsafe", "!", "!(", "!=", "&x")),
            workflow.replace("permissions:\n", "env:\n  NOTE: safe # comment\r  HIDDEN: &hidden safe\rpermissions:\n"),
            workflow.replace("permissions:\n", "env:\n  NOTE: 'safe\r    folded'\r  HIDDEN: &hidden safe\rpermissions:\n"),
            workflow.replace("permissions:\n", "env:\n  NOTE: |-\r    harmless\r  HIDDEN: &hidden safe\rpermissions:\n"),
            workflow.replace("permissions:\n", "env:\n  it's: &hidden payload\n  that's: *hidden\npermissions:\n"),
            workflow.replace("permissions:\n", "env:\n  foo'bar: !unsafe safe\n  foo\"bar: safe\npermissions:\n"),
            workflow.replace("permissions:\n", "env:\n  foo#bar: &hidden payload\n  baz#qux: *hidden\npermissions:\n"),
            *(workflow.replace("permissions:\n", f"env:\n  foo{mark}'bar: &hidden payload\n  baz{mark}'qux: *hidden\npermissions:\n") for mark in ("-", "?", ",", ":", " - ", " , ")),
            *(workflow.replace("permissions:\n", f"env:\n  foo{space}#bar: &hidden payload\n  baz{space}#qux: *hidden\npermissions:\n") for space in ("\u00a0", "\u2003", "\u202f")),
        )
        for candidate in mutations:
            self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", candidate))

    def test_duplicate_recognized_root_keys_fail_closed(self):
        for key, first_value, second_value in (
            ("on", "{}", "{workflow_dispatch: {}}"),
            ("permissions", "{}", "read-all"),
            ("jobs", "{}", "{verify: {runs-on: ubuntu-latest}}"),
        ):
            with self.subTest(key=key):
                workflow = (
                    f"name: proof\n{key}: {first_value}\n"
                    f"{key}: {second_value}\n"
                )
                findings = guard.workflow_inventory_violations(
                    {"proof.yml": workflow}, "proof.yml\n"
                )
                self.assertTrue(
                    any("duplicate top-level YAML key" in finding for finding in findings)
                )

    def test_complex_non_meta_yaml_content_after_canonical_name_is_allowed(self):
        workflow = self.valid_workflow().replace(
            "on:\n",
            "env:\n"
            "  DISPLAY: durable-identity-proof\n"
            "  NOTES: |\n"
            "    - durable-name\n"
            "on:\n",
        )
        self.assertEqual(
            [], guard.workflow_inventory_violations({"proof.yml": workflow}, "proof.yml\n")
        )

    def test_unrelated_fix_word_without_number_is_allowed(self):
        workflows = {"fix-cache.yml": self.valid_workflow("fix-cache")}
        self.assertEqual(
            [], guard.workflow_inventory_violations(workflows, "fix-cache.yml\n")
        )

    def test_workflow_contract_change_detection(self):
        self.assertTrue(guard.pr_changes_workflow_contract([".github/workflows/new.yml"]))
        self.assertTrue(guard.pr_changes_workflow_contract([guard.DURABLE_WORKFLOW_MANIFEST]))
        self.assertTrue(guard.pr_changes_workflow_contract([guard.GUARD_SOURCE]))
        self.assertFalse(guard.pr_changes_workflow_contract(["backend/pom.xml"]))

    def test_r5_overlay_validation_binds_both_disposable_resources(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/application-ci.yml").read_text(encoding="utf-8")
        self.assertIn("R5_NETWORK_NAME=specgraph-r5-ci-${{ github.run_id }}-${{ github.run_attempt }}", workflow)
        self.assertIn("R5_EMBEDDING_CACHE_VOLUME=specgraph-r5-ci-embedding-cache-${{ github.run_id }}-${{ github.run_attempt }}", workflow)

    def test_repository_durable_workflows_satisfy_structural_policy(self):
        root = Path(__file__).resolve().parents[1]
        names = guard.parse_durable_workflow_manifest(
            (root / guard.DURABLE_WORKFLOW_MANIFEST).read_text(encoding="utf-8")
        )
        for name in sorted(names):
            with self.subTest(name=name):
                text = (root / guard.WORKFLOW_DIR / name).read_text(encoding="utf-8")
                self.assertEqual([], guard.durable_workflow_policy_violations(name, text))

    def test_untrusted_and_obfuscated_triggers_are_rejected(self):
        forms = (
            "  pull_request:",
            "  pull_request_review:",
            "  pull_request_review_comment:",
            "  issue_comment:",
            '  "issue\\u005fcomment":',
            "  pull_request_target :",
            "on: [workflow_dispatch]",
        )
        for trigger in forms:
            with self.subTest(trigger=trigger):
                workflow = self.valid_workflow().replace("  workflow_dispatch:", trigger)
                findings = guard.durable_workflow_policy_violations("proof.yml", workflow)
                self.assertTrue(findings)

    def test_runner_and_condition_bypasses_are_rejected(self):
        workflow = self.valid_workflow()
        mutations = (
            workflow.replace(guard.PRIVATE_RUNNER, "    runs-on: ubuntu-latest"),
            workflow.replace(guard.PRIVATE_RUNNER, "      runs-on: [self-hosted]"),
            workflow.replace("    if:", '    "if":'),
            workflow.replace(" }}\n", " || true }}\n", 1),
            *(workflow.replace(" }}\n", f" && {clause} }}\n", 1) for clause in (
                "false", "1 == 0", "github.repository != github.repository", "contains('a','b')"
            )),
            workflow.replace(guard.PRIVATE_RUNNER, f"{guard.PRIVATE_RUNNER}\n{guard.PRIVATE_RUNNER}"),
            workflow.replace("    steps:", "    if: ${{ true }}\n    steps:"),
        )
        for candidate in mutations:
            with self.subTest(candidate=candidate):
                self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", candidate))

    def test_job_id_grammar_accepts_underscore_and_rejects_numeric_start(self):
        valid = self.valid_workflow().replace("  verify:", "  _verify:")
        self.assertEqual([], guard.durable_workflow_policy_violations("proof.yml", valid))
        invalid = self.valid_workflow().replace("  verify:", "  1verify:")
        self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", invalid))
        self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", valid.replace("jobs:\n", "jobs:\n  \"bypass\":\n    runs-on: ubuntu-latest\n    steps:\n      - run: 'true'\n")))

    def test_concurrency_dag_and_matrix_are_fail_closed(self):
        workflow = self.valid_workflow()
        mutations = (
            workflow.replace("  queue: max\n", ""),
            workflow.replace("'specgraph-repository-queue'", "'other-queue'"),
            workflow.replace("    steps:\n", "    strategy:\n      matrix:\n        value: [a, b]\n    steps:\n"),
            workflow.replace("    steps:\n", "    strategy:\n      max-parallel: 2\n      matrix:\n        value: [a, b]\n    steps:\n"),
            workflow.replace("    steps:\n", "    strategy:\n        matrix:\n          value: [a, b]\n    steps:\n"),
            workflow.replace("    steps:\n", "    strategy: {matrix: {value: [a, b]}}\n    steps:\n"),
            workflow + "  parallel:\n" + workflow.split("  verify:\n", 1)[1],
            workflow + "  dependent:\n    needs: verify\n" + workflow.split("    if: ", 1)[1].join(("    if: ", "")),
        )
        self.assertTrue(all(guard.durable_workflow_policy_violations("proof.yml", item) for item in mutations))

    def test_external_actions_must_use_exact_commit(self):
        workflow = self.valid_workflow()
        pinned = workflow.replace("      - run: 'true'", "      - uses: actions/checkout@" + "a" * 40)
        self.assertEqual([], guard.durable_workflow_policy_violations("proof.yml", pinned))
        docker_digest = workflow.replace("      - run: 'true'", "      - uses: docker://alpine@sha256:" + "a" * 64)
        self.assertEqual([], guard.durable_workflow_policy_violations("proof.yml", docker_digest))
        refs = ("actions/checkout@v7", '"uses": actions/checkout@' + "a" * 40,
                "docker://alpine:latest", "./candidate-controlled-action")
        self.assertTrue(all(guard.durable_workflow_policy_violations(
            "proof.yml", workflow.replace("      - run: 'true'", "      - uses: " + ref)
        ) for ref in refs))

    def test_projects_token_and_implicit_permissions_are_rejected(self):
        workflow = self.valid_workflow()
        mutations = (
            workflow.replace("      - run: 'true'", "      - run: echo ${{ secrets.PROJECTS_TOKEN }}"),
            workflow.replace("      - run: 'true'", "      - run: echo ${{ secrets[format('{0}{1}', 'PROJECTS_', 'TOKEN')] }}"),
            workflow.replace("      - run: 'true'", "      - run: echo ${{ SeCrEtS.PROJECTS_TOKEN }}"),
        )
        self.assertTrue(all(guard.durable_workflow_policy_violations("proof.yml", item) for item in mutations))
        self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", workflow.replace("permissions:\n  contents: read\n\n", "")))

    def test_required_context_names_and_green_bypasses_are_reserved(self):
        workflow = self.valid_workflow()
        mutations = (
            workflow.replace("on:\n", "run-name: work-graph-integrity\non:\n"),
            workflow.replace("    runs-on:", "    continue-on-error: true\n    runs-on:"),
            workflow.replace("      - run:", "      - continue-on-error: true\n        run:"),
            workflow.replace("      - run: 'true'", "      - name: bypass\n        if: false\n        run: 'true'"),
        )
        self.assertTrue(all(guard.durable_workflow_policy_violations("proof.yml", item) for item in mutations))
        protected = (Path(__file__).resolve().parents[1] / ".github/workflows/work-graph-guard.yml").read_text(encoding="utf-8")
        disabled = protected.replace("      - name: Verify guard semantics\n", "      - name: Verify guard semantics\n        if: failure()\n")
        self.assertTrue(
            any("must run unconditionally" in item for item in guard.durable_workflow_policy_violations("work-graph-guard.yml", disabled))
        )

    def test_one_shot_references_in_trigger_or_if_are_rejected(self):
        workflow = self.valid_workflow()
        trigger_ref = workflow.replace("  workflow_dispatch:", "  workflow_dispatch:\n    # issue #42")
        condition_ref = workflow.replace("github.event_name == 'workflow_dispatch'", "github.event_name == 'workflow_dispatch' && github.event.issue.number != 42")
        self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", trigger_ref))
        self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", condition_ref))
        for expression in (
            "github.event.issue.number != 0xC0",
            "github.event.pull_request.number != 191 + 1",
            "github.event.number != 192",
        ):
            candidate = workflow.replace("github.event_name == 'workflow_dispatch'", "github.event_name == 'workflow_dispatch' && " + expression)
            self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", candidate))
        for payload in (
            "      - run: gh issue close 192",
            "      - run: |\n          gh issue close \\\n            192",
            "      - run: gh is''sue close 192",
            '      - run: gh is""sue close 192',
            "        TARGET: >-\n          issue\n          192",
            "        TARGET: >1-\n          issue\n          192",
            "        TARGET: >2+\n          issue\n          192",
            "        TARGET_ISSUE: 192",
            "          pr: 192",
            "      - name: Fix 192\n        run: 'true'",
        ):
            candidate = workflow.replace("      - run: 'true'", payload)
            self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", candidate))
        self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", workflow.replace("  verify:", "  fix-192:")))
        self.assertTrue(guard.durable_workflow_policy_violations("proof.yml", workflow.replace("permissions:\n", "env:\n  DISCOVERY_219: true\npermissions:\n")))

    def test_guard_source_allows_only_self_or_digest_rotation(self):
        source = Path(guard.__file__).read_bytes().decode("utf-8")
        self.assertEqual([], guard.protected_guard_source_violations(source))
        alternate_endings = source.replace("\r\n", "\n").replace("\r", "\n")
        alternate_endings = alternate_endings.replace("\n", "\r\n") if alternate_endings == source else alternate_endings
        self.assertTrue(guard.protected_guard_source_violations(alternate_endings))
        self.assertTrue(guard.protected_guard_source_violations(source + "\n# bypass\n"))
        path = ".github/workflows/work-graph-guard-tests.yml"
        deployed = next(iter(guard.PROTECTED_ASSET_SHA256[path]))
        rotation = source.replace(f'"{deployed}",', f'"{deployed}",\n            "{"f" * 64}",', 1)
        self.assertEqual([], guard.protected_guard_source_violations(rotation))
        replacement = source.replace(deployed, "e" * 64, 1)
        self.assertTrue(guard.protected_guard_source_violations(replacement))
        successor = guard.hashlib.sha256(replacement.encode("utf-8")).hexdigest()
        with patch.object(guard, "APPROVED_GUARD_SUCCESSOR_SHA256", frozenset({successor})):
            self.assertEqual([], guard.protected_guard_source_violations(replacement))
        for suffix in ("; ALLOWED_TRIGGERS = frozenset()", "; bypass = lambda: True"):
            bypass = source.replace(
                "APPROVED_GUARD_SUCCESSOR_SHA256 = frozenset()",
                "APPROVED_GUARD_SUCCESSOR_SHA256 = frozenset()" + suffix,
            )
            self.assertTrue(guard.protected_guard_source_violations(bypass))

    def test_renamed_previous_paths_are_detected(self):
        changed = guard.changed_file_paths([
            {
                "filename": "docs/retired-proof.yml",
                "previous_filename": ".github/workflows/pr-42-proof.yml",
            },
            {
                "filename": "scripts/ci/old-workflow-list.txt",
                "previous_filename": guard.DURABLE_WORKFLOW_MANIFEST,
            },
        ])
        self.assertTrue(guard.pr_changes_workflow_contract(changed))


    def test_repository_protected_assets_match_pinned_contract(self):
        self.assertEqual(
            REQUIRED_PROTECTED_ASSETS,
            set(guard.PROTECTED_ASSET_SHA256),
        )
        root = Path(__file__).resolve().parents[1]
        for path in REQUIRED_PROTECTED_ASSETS:
            with self.subTest(path=path):
                content = (root / path).read_text(encoding="utf-8")
                self.assertEqual([], guard.protected_asset_violations(path, content))

    def test_required_assets_trigger_current_and_previous_path_checks(self):
        for path in REQUIRED_PROTECTED_ASSETS:
            with self.subTest(path=path):
                self.assertTrue(guard.pr_changes_workflow_contract([path]))
                renamed = guard.changed_file_paths([{
                    "filename": "retired/asset",
                    "previous_filename": path,
                }])
                self.assertTrue(guard.pr_changes_workflow_contract(renamed))

    def test_protected_digest_allowlist_is_bounded(self):
        path = "scripts/test_work_graph_guard.py"
        for allowed in (frozenset(), frozenset({"a", "b", "c"})):
            with self.subTest(size=len(allowed)):
                with patch.dict(guard.PROTECTED_ASSET_SHA256, {path: allowed}):
                    findings = guard.protected_asset_violations(path, "content")
                self.assertTrue(any("one or two entries" in item for item in findings))

    def test_protected_assets_fail_closed_on_no_op_mutations(self):
        root = Path(__file__).resolve().parents[1]
        workflow_path = ".github/workflows/work-graph-guard-tests.yml"
        workflow = (root / workflow_path).read_text(encoding="utf-8")
        no_op_workflow = workflow.replace(
            "        run: python3 -B -m unittest scripts/test_work_graph_guard.py",
            "        run: echo tests-disabled",
        )
        self.assertTrue(guard.protected_asset_violations(workflow_path, no_op_workflow))

        test_path = "scripts/test_work_graph_guard.py"
        tests = (root / test_path).read_text(encoding="utf-8")
        no_op_tests = tests.replace(
            "class DurableWorkflowTests(unittest.TestCase):",
            "@unittest.skip(\"disabled\")\nclass DurableWorkflowTests(unittest.TestCase):",
        )
        self.assertTrue(guard.protected_asset_violations(test_path, no_op_tests))

        project_script = ".github/scripts/project-v2-reconcile.cjs"
        script = (root / project_script).read_text(encoding="utf-8")
        self.assertTrue(guard.protected_asset_violations(project_script, script + "\n// no-op\n"))
        self.assertTrue(guard.protected_asset_violations(project_script, "module.exports = async () => {};\n"))

        self.assertTrue(guard.protected_asset_violations(guard.DURABLE_WORKFLOW_MANIFEST, ""))

    def test_inventory_applies_pinned_trusted_guard_contract(self):
        workflow = "name: work-graph-guard\non:\n  workflow_dispatch:\n"
        self.assertEqual(
            [],
            guard.canonical_workflow_name_violations(
                "work-graph-guard.yml", workflow
            ),
        )
        findings = guard.workflow_inventory_violations(
            {"work-graph-guard.yml": workflow}, "work-graph-guard.yml\n")
        self.assertTrue(any("protected asset changed" in finding for finding in findings))

    def test_protected_workflow_deletion_cannot_hide_in_manifest_change(self):
        findings = guard.workflow_inventory_violations(
            {},
            "",
            require_protected_workflows=True,
        )
        self.assertTrue(
            any(
                ".github/workflows/work-graph-guard.yml: protected asset is missing"
                == finding
                for finding in findings
            )
        )
        self.assertTrue(
            any(
                ".github/workflows/work-graph-guard-tests.yml: protected asset is missing"
                == finding
                for finding in findings
            )
        )


class RunnerResourceIsolationTests(unittest.TestCase):
    ROOT = Path(__file__).resolve().parents[1]

    def workflow(self, name):
        return (self.ROOT / ".github" / "workflows" / name).read_text(encoding="utf-8")

    def test_mutable_runtime_identities_include_run_attempt(self):
        for name in (
            "application-ci.yml", "demo-images.yml", "r4-acceptance-ci.yml",
            "r4-auth-ci.yml", "r4-gallery-ci.yml", "r4-retrieval-ci.yml",
            "r5-release.yml",
        ):
            with self.subTest(name=name):
                text = self.workflow(name)
                for line in text.splitlines():
                    if any(marker in line for marker in (
                        "COMPOSE_PROJECT_NAME:", "BUILDX_BUILDER:",
                        "CACHE_VOLUME:", "NETWORK_NAME:", "STUB_NAME:",
                    )) and "specgraph-" in line:
                        self.assertIn("github.run_attempt", line)
                self.assertNotIn("GITHUB_RUN_ID %", text)

    def test_builds_use_and_reclaim_run_scoped_builder(self):
        for name in (
            "application-ci.yml", "r4-acceptance-ci.yml", "r4-auth-ci.yml",
            "r4-gallery-ci.yml", "r4-retrieval-ci.yml", "r5-release.yml",
        ):
            with self.subTest(name=name):
                text = self.workflow(name)
                self.assertIn("run-scoped-buildx.sh prepare", text)
                self.assertIn("run-scoped-buildx.sh cleanup", text)
                self.assertIn("BUILDKIT_CACHE_MOUNT_NS:", text)
        for helper in ("ensure-app-image.sh", "ensure-e2e-image.sh"):
            text = (self.ROOT / "scripts" / "ci" / helper).read_text(encoding="utf-8")
            self.assertIn('docker buildx build --builder "${BUILDX_BUILDER:', text)
            self.assertIn('BUILDKIT_CACHE_MOUNT_NS=${BUILDKIT_CACHE_MOUNT_NS:', text)

    def test_r4_and_r5_writable_model_caches_are_attempt_scoped(self):
        r4 = self.workflow("r4-acceptance-ci.yml")
        self.assertIn("R4_EMBEDDING_CACHE_VOLUME: specgraph-r4-ci-embedding-cache-${{ github.run_id }}-${{ github.run_attempt }}", r4)
        self.assertLess(r4.index('docker volume rm -f "$R4_EMBEDDING_CACHE_VOLUME"'), r4.index("docker volume create"))
        self.assertIn('docker volume rm -f "$R4_EMBEDDING_CACHE_VOLUME"', r4)
        self.assertFalse((self.ROOT / "scripts" / "ci" / "ensure-r4-embedding-cache.sh").exists())
        for name in ("r4-acceptance-ci.yml", "r5-release.yml"):
            self.assertNotIn("ensure-r4-embedding-cache.sh", self.workflow(name))
        overlay = (self.ROOT / "compose.r5.ci.yaml").read_text(encoding="utf-8")
        self.assertIn("R5_NETWORK_NAME:?", overlay)
        self.assertIn("R5_EMBEDDING_CACHE_VOLUME:?", overlay)
        retrieval = self.workflow("r4-retrieval-ci.yml")
        self.assertIn("R4_PORT: '0'", retrieval)
        self.assertIn('docker port "$application" 8080/tcp', retrieval)

    def test_native_caches_and_credentials_are_attempt_scoped_and_cleaned(self):
        application = self.workflow("application-ci.yml")
        source = self.workflow("source-reference.yml")
        self.assertIn("npm_config_cache: ${{ runner.temp }}/specgraph-npm-${{ github.run_id }}-${{ github.run_attempt }}", application)
        self.assertIn("MAVEN_CACHE_DIR: ${{ runner.temp }}/specgraph-maven-${{ github.run_id }}-${{ github.run_attempt }}", source)
        self.assertIn("docs/tooling/frontend-reference/node_modules", source)
        for name in ("demo-images.yml", "r5-release.yml"):
            text = self.workflow(name)
            self.assertIn("DOCKER_CONFIG: /tmp/specgraph-docker-${{ github.run_id }}-${{ github.run_attempt }}", text)
            self.assertIn('rm -rf "$DOCKER_CONFIG"', text)


if __name__ == "__main__":
    unittest.main()
