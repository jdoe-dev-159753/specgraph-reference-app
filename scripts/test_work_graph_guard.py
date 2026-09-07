#!/usr/bin/env python3

import base64
import unittest
from unittest.mock import Mock, patch
from pathlib import Path

from scripts import work_graph_guard as guard


REQUIRED_PROTECTED_ASSETS = {
    ".github/workflows/work-graph-guard.yml",
    ".github/workflows/work-graph-guard-tests.yml",
    "scripts/test_work_graph_guard.py",
}


class ReviewFreshnessTests(unittest.TestCase):
    @staticmethod
    def clean_summary(prefix="3f8fc1e"):
        return {
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": guard.CODEX_APP_ID},
            "body": (
                "<!-- codex-pull-request-review-summary -->\n\n"
                "| Review | Status | Commit | Review trigger |\n"
                "| --- | --- | --- | --- |\n"
                f"| 📝 **Code Review** | ✅ **Completed** now | `{prefix}` | Draft marked ready |"
            ),
        }

    @staticmethod
    def codex_approval(user_id=guard.CODEX_USER_ID):
        return {"content": "+1", "user": {"id": user_id}}

    def test_pull_request_event_resolves_pr_number(self):
        event = {"number": 42, "pull_request": {"number": 42}}
        self.assertEqual(42, guard.event_pr_number_from_payload(event))

    def test_bot_issue_comment_on_pull_request_resolves_pr_number(self):
        event = {"issue": {"number": 43, "pull_request": {"url": "pr"}}}
        self.assertEqual(43, guard.event_pr_number_from_payload(event))

    def test_plain_issue_event_has_no_pr_number(self):
        self.assertIsNone(guard.event_pr_number_from_payload({"issue": {"number": 44}}))

    def test_current_head_codex_review_is_accepted(self):
        reviews = [
            {
                "commit_id": "abc123",
                "user": {"id": guard.CODEX_USER_ID, "login": "chatgpt-codex-connector[bot]"},
            }
        ]
        self.assertTrue(guard.has_current_head_codex_review(reviews, "abc123"))

    def test_superseded_codex_review_is_rejected(self):
        reviews = [
            {
                "commit_id": "old123",
                "user": {"id": guard.CODEX_USER_ID, "login": "chatgpt-codex-connector[bot]"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "new456"))

    def test_prefix_collision_reviewer_is_rejected(self):
        reviews = [
            {
                "commit_id": "abc123",
                "user": {"id": 123456, "login": "chatgpt-codex-connector-fake"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "abc123"))

    def test_human_review_on_current_head_does_not_substitute_for_codex(self):
        reviews = [
            {
                "commit_id": "abc123",
                "user": {"id": 9963055, "login": "repository-owner"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "abc123"))

    def test_clean_codex_comment_on_current_head_is_accepted(self):
        head = "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
        comments = [{
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": guard.CODEX_APP_ID},
            "body": "Codex Review: Didn't find any major issues. :rocket:\n\n**Reviewed commit:** `3f8fc1e6e8`",
        }]
        self.assertTrue(guard.has_current_head_clean_codex_result(comments, head))

    def test_clean_codex_comment_on_superseded_head_is_rejected(self):
        comments = [{
            "user": {"id": guard.CODEX_USER_ID},
            "performed_via_github_app": {"id": guard.CODEX_APP_ID},
            "body": "Codex Review: Didn't find any major issues. :rocket:\n\n**Reviewed commit:** `3f8fc1e6e8`",
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
            "body": "Codex Review: Didn't find any major issues. :rocket:\n\n**Reviewed commit:** `3f8fc1e6e8`",
        }]
        self.assertFalse(
            guard.has_current_head_clean_codex_result(
                comments, "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
            )
        )

    def test_completed_codex_summary_with_approval_is_accepted(self):
        head = "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
        self.assertTrue(
            guard.has_current_head_clean_codex_summary(
                [self.clean_summary()], [self.codex_approval()], head
            )
        )

    def test_running_codex_summary_is_rejected(self):
        summary = self.clean_summary()
        summary["body"] = summary["body"].replace("**Completed**", "**Running**")
        self.assertFalse(
            guard.has_current_head_clean_codex_summary(
                [summary], [self.codex_approval()], "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
            )
        )

    def test_stale_codex_summary_is_rejected(self):
        self.assertFalse(
            guard.has_current_head_clean_codex_summary(
                [self.clean_summary("aaaaaaaa")],
                [self.codex_approval()],
                "3f8fc1e6e80d0449e548795dc66154aa18f3815d",
            )
        )

    def test_completed_summary_without_codex_approval_is_rejected(self):
        self.assertFalse(
            guard.has_current_head_clean_codex_summary(
                [self.clean_summary()], [], "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
            )
        )

    def test_completed_summary_with_wrong_user_approval_is_rejected(self):
        self.assertFalse(
            guard.has_current_head_clean_codex_summary(
                [self.clean_summary()],
                [self.codex_approval(user_id=123456)],
                "3f8fc1e6e80d0449e548795dc66154aa18f3815d",
            )
        )

    def test_completed_summary_from_wrong_app_is_rejected(self):
        summary = self.clean_summary()
        summary["performed_via_github_app"]["id"] = 1
        self.assertFalse(
            guard.has_current_head_clean_codex_summary(
                [summary],
                [self.codex_approval()],
                "3f8fc1e6e80d0449e548795dc66154aa18f3815d",
            )
        )


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
    def test_real_review_guard_accepts_current_clean_summary_reaction(self):
        head = "3f8fc1e6e80d0449e548795dc66154aa18f3815d"
        pull_request = {
            "state": "open",
            "draft": False,
            "base": {"ref": "main"},
            "head": {"sha": head},
        }
        page_results = iter((
            iter(()),
            iter((ReviewFreshnessTests.clean_summary(),)),
            iter((ReviewFreshnessTests.codex_approval(),)),
        ))

        with (
            patch.object(guard, "api", return_value=pull_request),
            patch.object(guard, "pages", side_effect=lambda _path: next(page_results)),
        ):
            failures = []
            guard.require_current_head_codex_review(326, failures)

        self.assertEqual([], failures)

    def test_main_runs_both_pr_guards_and_propagates_each_failure(self):
        injectors = (
            "require_durable_workflow_surface",
            "require_current_head_codex_review",
        )
        for failing_guard in injectors:
            with self.subTest(failing_guard=failing_guard):
                def inject_failure(pr_number, failures):
                    self.assertEqual(308, pr_number)
                    failures.append(f"{failing_guard} injected failure")

                with (
                    patch.object(guard, "pages", return_value=iter(())),
                    patch.object(guard, "event_pr_number", return_value=308),
                    patch.object(guard, "require_durable_workflow_surface") as durable,
                    patch.object(guard, "require_current_head_codex_review") as review,
                ):
                    selected = {
                        "require_durable_workflow_surface": durable,
                        "require_current_head_codex_review": review,
                    }
                    selected[failing_guard].side_effect = inject_failure
                    self.assertEqual(1, guard.main())
                    durable.assert_called_once()
                    review.assert_called_once()


    def test_real_review_guard_rejects_missing_exact_head_review(self):
        pull_request = {
            "state": "open",
            "draft": False,
            "base": {"ref": "main"},
            "head": {"sha": "a" * 40},
        }

        with (
            patch.object(guard, "api", return_value=pull_request),
            patch.object(guard, "pages", return_value=iter(())),
        ):
            failures = []
            guard.require_current_head_codex_review(308, failures)

        self.assertEqual(1, len(failures))
        self.assertIn("no Codex review evidence", failures[0])

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
            if "/contents/.github/workflows?" in path:
                return []
            if "/contents/scripts/test_work_graph_guard.py?" in path:
                return {
                    "type": "file",
                    "encoding": "base64",
                    "content": base64.b64encode(
                        Path(__file__).read_bytes() + b"\n"
                    ).decode("ascii"),
                }
            raise AssertionError(f"unexpected API path: {path}")

        changed = iter(([{"filename": guard.DURABLE_WORKFLOW_MANIFEST}],))
        with (
            patch.object(guard, "api", side_effect=fake_api),
            patch.object(guard, "pages", side_effect=changed),
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
    def test_guard_source_digest_is_line_ending_exact(self):
        source = Path(guard.__file__).read_bytes().decode("utf-8")
        self.assertEqual([], guard.protected_guard_source_violations(source))
        alternate = source.replace("\r\n", "\n").replace("\r", "\n")
        alternate = alternate.replace("\n", "\r\n") if alternate == source else alternate
        self.assertTrue(guard.protected_guard_source_violations(alternate))

    def test_parse_manifest_ignores_comments_and_blank_lines(self):
        manifest = "# durable\napplication-ci.yml\n\n r4-acceptance-ci.yml \n"
        self.assertEqual(
            {"application-ci.yml", "r4-acceptance-ci.yml"},
            guard.parse_durable_workflow_manifest(manifest),
        )

    def test_exact_canonical_inventory_is_accepted(self):
        workflows = {
            "application-ci.yml": "name: application-ci\non:\n  workflow_dispatch:\n",
            "r4-acceptance-ci.yml": "name: r4-acceptance-ci\non:\n  workflow_dispatch:\n",
        }
        manifest = "application-ci.yml\nr4-acceptance-ci.yml\n"
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
        workflows = {"discovery-219-fix.yml": "name: discovery-219-fix\n"}
        findings = guard.workflow_inventory_violations(
            workflows, "discovery-219-fix.yml\n"
        )
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_numbered_identity_markers_are_rejected(self):
        separators = ("-", "_", " ", ".", "#", ":")
        for keyword in ("pr", "pull-request", "pull request", "issue", "discovery", "story", "fix"):
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
            "issue-numbering-42",
            "story-identity-9",
            "pull-request-idempotency-3",
            "fix-no-cache-2",
            "pr-not-17",
            "issue-no",
            "version-42",
            "issueid42",
            "storyno7",
            "fixid8",
            "prnumber17",
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

    def test_complex_yaml_content_after_canonical_name_is_irrelevant(self):
        workflow = (
            "name: proof\n"
            "env:\n"
            "  DISPLAY: &identity issue-42-proof\n"
            "  NOTES: |\n"
            "    - &identity durable-name\n"
            "jobs: {}\n"
        )
        self.assertEqual(
            [], guard.workflow_inventory_violations({"proof.yml": workflow}, "proof.yml\n")
        )

    def test_unrelated_fix_word_without_number_is_allowed(self):
        workflows = {"fix-cache.yml": "name: fix-cache\n"}
        self.assertEqual(
            [], guard.workflow_inventory_violations(workflows, "fix-cache.yml\n")
        )

    def test_workflow_contract_change_detection(self):
        self.assertTrue(guard.pr_changes_workflow_contract([".github/workflows/new.yml"]))
        self.assertTrue(guard.pr_changes_workflow_contract([guard.DURABLE_WORKFLOW_MANIFEST]))
        self.assertFalse(guard.pr_changes_workflow_contract(["backend/pom.xml"]))

    def test_r5_overlay_validation_binds_both_disposable_resources(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/application-ci.yml").read_text(encoding="utf-8")
        self.assertIn("R5_NETWORK_NAME=specgraph-r5-ci-${{ github.run_id }}-${{ github.run_attempt }}", workflow)
        self.assertIn("R5_EMBEDDING_CACHE_VOLUME=specgraph-r5-ci-embedding-cache-${{ github.run_id }}-${{ github.run_attempt }}", workflow)

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
