#!/usr/bin/env python3

import unittest

from scripts import work_graph_guard as guard


class ReviewFreshnessTests(unittest.TestCase):
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


class DurableWorkflowTests(unittest.TestCase):
    def test_parse_manifest_ignores_comments_and_blank_lines(self):
        manifest = "# durable\napplication-ci.yml\n\n r4-acceptance-ci.yml \n"
        self.assertEqual(
            {"application-ci.yml", "r4-acceptance-ci.yml"},
            guard.parse_durable_workflow_manifest(manifest),
        )

    def test_exact_durable_inventory_is_accepted(self):
        workflows = {
            "application-ci.yml": "name: application-ci\non:\n  workflow_dispatch:\n",
            "r4-acceptance-ci.yml": "name: r4-acceptance-ci\non:\n  workflow_dispatch:\n",
        }
        manifest = "application-ci.yml\nr4-acceptance-ci.yml\n"
        self.assertEqual([], guard.workflow_inventory_violations(workflows, manifest))

    def test_undeclared_workflow_is_rejected(self):
        workflows = {
            "application-ci.yml": "name: application-ci\n",
            "temporary-proof.yml": "name: temporary-proof\n",
        }
        findings = guard.workflow_inventory_violations(workflows, "application-ci.yml\n")
        self.assertTrue(any("not declared durable" in finding for finding in findings))

    def test_missing_declared_workflow_is_rejected(self):
        workflows = {"application-ci.yml": "name: application-ci\n"}
        findings = guard.workflow_inventory_violations(
            workflows, "application-ci.yml\nr4-acceptance-ci.yml\n"
        )
        self.assertTrue(any("missing from repository" in finding for finding in findings))

    def test_numbered_one_shot_filename_is_rejected(self):
        workflows = {"discovery-219-fix.yml": "name: durable-looking-name\n"}
        findings = guard.workflow_inventory_violations(
            workflows, "discovery-219-fix.yml\n"
        )
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_numbered_one_shot_workflow_name_is_rejected(self):
        workflows = {"proof.yml": "name: story-42-proof\n"}
        findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_human_readable_numbered_workflow_names_are_rejected(self):
        for workflow_name in ("PR #42 proof", "Issue 42 validation", "discovery #219 fix"):
            with self.subTest(workflow_name=workflow_name):
                workflows = {"proof.yml": f'name: "{workflow_name}"\n'}
                findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
                self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_quoted_yaml_name_keys_are_rejected(self):
        for workflow in ('"name": issue-42-proof\n', "'name': story-43-proof\n"):
            with self.subTest(workflow=workflow):
                workflows = {"proof.yml": workflow}
                findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
                self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_escaped_double_quoted_yaml_name_keys_are_rejected(self):
        for key in (r"na\x6de", r"na\u006de", r"na\U0000006de"):
            with self.subTest(key=key):
                workflow = f'"{key}": issue-42-proof\n'
                self.assertEqual("issue-42-proof", guard.extract_workflow_name(workflow))
                findings = guard.workflow_inventory_violations(
                    {"proof.yml": workflow}, "proof.yml\n"
                )
                self.assertTrue(
                    any("one-shot workflow identity" in finding for finding in findings)
                )

    def test_multiline_double_quoted_yaml_names_are_rejected(self):
        escaped = 'name: "issue-' + chr(92) + '\n  42-proof"\n'
        folded = 'name: "issue-\n  42-proof"\n'
        for workflow, expected in (
            (escaped, "issue-42-proof"),
            (folded, "issue- 42-proof"),
        ):
            with self.subTest(workflow=workflow):
                self.assertEqual(expected, guard.extract_workflow_name(workflow))
                findings = guard.workflow_inventory_violations(
                    {"proof.yml": workflow}, "proof.yml\n"
                )
                self.assertTrue(
                    any("one-shot workflow identity" in finding for finding in findings)
                )

    def test_multiline_plain_and_single_quoted_names_are_rejected(self):
        for workflow, expected in (
            ("name: issue-\n  42-proof\n", "issue- 42-proof"),
            ("name: 'issue-\n  42-proof'\n", "issue- 42-proof"),
        ):
            with self.subTest(workflow=workflow):
                self.assertEqual(expected, guard.extract_workflow_name(workflow))
                findings = guard.workflow_inventory_violations(
                    {"proof.yml": workflow}, "proof.yml\n"
                )
                self.assertTrue(
                    any("one-shot workflow identity" in finding for finding in findings)
                )

    def test_punctuation_delimited_workflow_identities_are_rejected(self):
        for workflow_name in ("Validate (Issue #42)", "Validate [story-7]"):
            with self.subTest(workflow_name=workflow_name):
                workflows = {"proof.yml": f"name: {workflow_name}\n"}
                findings = guard.workflow_inventory_violations(
                    workflows, "proof.yml\n"
                )
                self.assertTrue(
                    any("one-shot workflow identity" in finding for finding in findings)
                )

    def test_aliased_workflow_name_is_rejected(self):
        workflow = (
            'identity: &identity "issue-42-proof"\n'
            "name: *identity\n"
        )
        self.assertEqual("issue-42-proof", guard.extract_workflow_name(workflow))
        findings = guard.workflow_inventory_violations(
            {"proof.yml": workflow}, "proof.yml\n"
        )
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_full_pull_request_identities_are_rejected(self):
        workflows = {"pull-request-42-proof.yml": "name: durable-looking-name\n"}
        findings = guard.workflow_inventory_violations(workflows, "pull-request-42-proof.yml\n")
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

        workflows = {"proof.yml": 'name: "Pull Request #42 proof"\n'}
        findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_folded_block_workflow_name_is_rejected(self):
        workflows = {"proof.yml": "name: >-\n  PR #42 proof\non:\n  workflow_dispatch:\n"}
        findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_commented_folded_block_workflow_name_is_rejected(self):
        workflows = {"proof.yml": "name: >- # display name\n  issue-42-proof\non:\n  workflow_dispatch:\n"}
        findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_commented_quoted_workflow_names_are_rejected(self):
        for workflow in (
            'name: "issue-42-proof" # display name\n',
            "name: 'story-43-proof' # display name\n",
        ):
            with self.subTest(workflow=workflow):
                workflows = {"proof.yml": workflow}
                findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
                self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_hash_inside_quoted_workflow_name_is_preserved(self):
        self.assertEqual(
            "release #42 notes",
            guard.extract_workflow_name('name: "release #42 notes" # display name\n'),
        )

    def test_literal_block_with_indented_root_is_rejected(self):
        workflows = {"proof.yml": "  name: |\n    Issue 42 validation\n  on:\n    workflow_dispatch:\n"}
        findings = guard.workflow_inventory_violations(workflows, "proof.yml\n")
        self.assertTrue(any("one-shot workflow identity" in finding for finding in findings))

    def test_plain_yaml_comment_is_not_part_of_workflow_name(self):
        workflows = {"proof.yml": "name: PR #42 proof\n"}
        self.assertEqual([], guard.workflow_inventory_violations(workflows, "proof.yml\n"))
        self.assertEqual("PR", guard.extract_workflow_name(workflows["proof.yml"]))

    def test_unrelated_fix_word_without_number_is_allowed(self):
        workflows = {"repair.yml": "name: fix flaky cache reuse\n"}
        self.assertEqual([], guard.workflow_inventory_violations(workflows, "repair.yml\n"))

    def test_workflow_contract_change_detection(self):
        self.assertTrue(guard.pr_changes_workflow_contract([".github/workflows/new-proof.yml"]))
        self.assertTrue(guard.pr_changes_workflow_contract([guard.DURABLE_WORKFLOW_MANIFEST]))
        self.assertFalse(guard.pr_changes_workflow_contract(["backend/pom.xml"]))

    def test_rename_out_of_workflow_directory_is_detected(self):
        changed = guard.changed_file_paths([{
            "filename": "docs/retired-proof.yml",
            "previous_filename": ".github/workflows/pr-42-proof.yml",
        }])
        self.assertTrue(guard.pr_changes_workflow_contract(changed))

    def test_manifest_rename_is_detected_from_previous_filename(self):
        changed = guard.changed_file_paths([{
            "filename": "scripts/ci/old-workflow-list.txt",
            "previous_filename": guard.DURABLE_WORKFLOW_MANIFEST,
        }])
        self.assertTrue(guard.pr_changes_workflow_contract(changed))


if __name__ == "__main__":
    unittest.main()
