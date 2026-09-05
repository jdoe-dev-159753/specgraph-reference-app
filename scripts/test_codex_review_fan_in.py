import unittest
from pathlib import Path

from scripts import codex_review_fan_in as fan_in


class WorkflowSelectionTests(unittest.TestCase):
    def test_backend_change_requires_application_and_r4(self):
        self.assertEqual(
            {"application-ci", "r4-acceptance-ci"},
            fan_in.expected_workflows(["backend/src/main/java/App.java"]),
        )

    def test_dataset_ceiling_paths_require_application(self):
        for path in (
            "scripts/analyze_dataset_ceiling.py",
            "scripts/test_analyze_dataset_ceiling.py",
            "docs/analysis/dataset-ceiling.md",
        ):
            with self.subTest(path=path):
                self.assertEqual({"application-ci"}, fan_in.expected_workflows([path]))

    def test_only_root_r4_scripts_require_application(self):
        self.assertIn("application-ci", fan_in.expected_workflows(["scripts/r4-check.sh"]))
        self.assertNotIn(
            "application-ci", fan_in.expected_workflows(["scripts/r4-tools/check.sh"])
        )

    def test_diagram_change_requires_plantuml(self):
        self.assertEqual(
            {"plantuml-diagrams"},
            fan_in.expected_workflows(["docs/assignment/SDD/diagrams/model.puml"]),
        )

    def test_plain_documentation_has_no_executable_gate(self):
        self.assertEqual(set(), fan_in.expected_workflows(["README.md"]))

    def test_fan_in_change_requires_its_tests(self):
        self.assertEqual(
            {"codex-review-fan-in-tests"},
            fan_in.expected_workflows(["scripts/codex_review_fan_in.py"]),
        )

    def test_durable_manifest_requires_guard_tests_and_r4(self):
        self.assertEqual(
            {"r4-acceptance-ci", "work-graph-guard-tests"},
            fan_in.expected_workflows(["scripts/ci/durable-workflows.txt"]),
        )

    def test_retarget_to_main_is_a_scoped_trigger(self):
        workflows = (
            "application-ci.yml", "r4-acceptance-ci.yml", "plantuml-diagrams.yml",
            "codex-review-fan-in.yml", "codex-review-fan-in-tests.yml",
        )
        for name in workflows:
            source = Path(f".github/workflows/{name}").read_text(encoding="utf-8")
            with self.subTest(workflow=name):
                self.assertIn("edited", source)
                self.assertIn("github.event.changes.base != null", source)

    def test_metadata_edits_cannot_cancel_verification_runs(self):
        workflows = (
            "application-ci.yml", "r4-acceptance-ci.yml", "plantuml-diagrams.yml",
            "codex-review-fan-in.yml", "codex-review-fan-in-tests.yml",
        )
        for name in workflows:
            source = Path(f".github/workflows/{name}").read_text(encoding="utf-8")
            with self.subTest(workflow=name):
                self.assertIn(
                    "github.event.changes.base == null && github.run_id", source
                )

    def test_workflow_source_changes_require_manual_review(self):
        self.assertTrue(fan_in.requires_manual_review([".github/workflows/application-ci.yml"]))
        self.assertTrue(fan_in.requires_manual_review(["scripts/work_graph_guard.py"]))
        self.assertTrue(fan_in.requires_manual_review(["scripts/codex_review_fan_in.py"]))
        self.assertFalse(fan_in.requires_manual_review(["backend/src/main/java/App.java"]))


class GateTests(unittest.TestCase):
    def test_all_expected_successes_are_ready(self):
        state, names = fan_in.gate_state(
            {"application-ci", "r4-acceptance-ci"},
            [
                {
                    "id": 1, "name": "application-ci", "event": "pull_request",
                    "path": fan_in.WORKFLOW_PATHS["application-ci"],
                    "status": "completed", "conclusion": "success",
                    "jobs": [{"name": "fast-verify", "conclusion": "success"}],
                },
                {
                    "id": 2, "name": "r4-acceptance-ci", "event": "pull_request",
                    "path": fan_in.WORKFLOW_PATHS["r4-acceptance-ci"],
                    "status": "completed", "conclusion": "success",
                    "jobs": [{"name": "verify-r4-acceptance", "conclusion": "success"}],
                },
            ],
        )
        self.assertEqual(("ready", []), (state, names))

    def test_missing_or_running_workflow_waits(self):
        state, names = fan_in.gate_state(
            {"application-ci", "r4-acceptance-ci"},
            [{
                "id": 1, "name": "application-ci", "event": "pull_request",
                "path": fan_in.WORKFLOW_PATHS["application-ci"],
                "status": "in_progress",
            }],
        )
        self.assertEqual("waiting", state)
        self.assertEqual(["r4-acceptance-ci", "application-ci"], names)

    def test_failure_blocks_review(self):
        self.assertEqual(
            ("blocked", ["application-ci"]),
            fan_in.gate_state(
                {"application-ci"},
                [{
                    "id": 1, "name": "application-ci", "event": "pull_request",
                    "path": fan_in.WORKFLOW_PATHS["application-ci"],
                    "status": "completed", "conclusion": "failure",
                }],
            ),
        )

    def test_latest_attempt_wins(self):
        self.assertEqual(
            ("ready", []),
            fan_in.gate_state(
                {"application-ci"},
                [
                    {
                        "id": 1, "name": "application-ci", "event": "pull_request",
                        "path": fan_in.WORKFLOW_PATHS["application-ci"],
                        "status": "completed", "conclusion": "failure",
                    },
                    {
                        "id": 2, "name": "application-ci", "event": "workflow_dispatch",
                        "path": fan_in.WORKFLOW_PATHS["application-ci"],
                        "status": "completed", "conclusion": "success",
                        "jobs": [{"name": "fast-verify", "conclusion": "success"}],
                    },
                ],
            ),
        )

    def test_every_successful_run_requires_its_verification_job(self):
        for name, required_job in fan_in.VERIFICATION_JOBS.items():
            failed_run = {
                "id": 1, "name": name, "event": "pull_request",
                "path": fan_in.WORKFLOW_PATHS[name],
                "status": "completed", "conclusion": "failure",
            }
            for event in ("pull_request", "workflow_dispatch"):
                candidate = {
                    "id": 2, "name": name, "event": event,
                    "path": fan_in.WORKFLOW_PATHS[name],
                    "status": "completed", "conclusion": "success",
                    "jobs": [{"name": "non-verifying-job", "conclusion": "success"}],
                }
                with self.subTest(workflow=name, event=event, verification="missing"):
                    self.assertEqual(
                        ("blocked", [name]),
                        fan_in.gate_state({name}, [failed_run, candidate]),
                    )
                candidate["jobs"] = [{"name": required_job, "conclusion": "success"}]
                with self.subTest(workflow=name, event=event, verification="success"):
                    self.assertEqual(
                        ("ready", []), fan_in.gate_state({name}, [failed_run, candidate])
                    )

    def test_skipped_metadata_edit_does_not_hide_same_head_success(self):
        real_run = {
            "id": 1, "name": "application-ci", "event": "pull_request",
            "path": fan_in.WORKFLOW_PATHS["application-ci"],
            "status": "completed", "conclusion": "success",
            "jobs": [{"name": "fast-verify", "conclusion": "success"}],
        }
        skipped_edit = {
            "id": 2, "name": "application-ci", "event": "pull_request",
            "path": fan_in.WORKFLOW_PATHS["application-ci"],
            "status": "completed", "conclusion": "skipped",
            "jobs": [{"name": "fast-verify", "conclusion": "skipped"}],
        }
        self.assertEqual(
            ("ready", []), fan_in.gate_state({"application-ci"}, [real_run, skipped_edit])
        )

    def test_same_name_from_noncanonical_workflow_is_ignored(self):
        run = {
            "id": 1, "name": "application-ci", "event": "pull_request",
            "path": ".github/workflows/spoofed-application-ci.yml",
            "status": "completed", "conclusion": "success",
        }
        self.assertEqual(("waiting", ["application-ci"]), fan_in.gate_state({"application-ci"}, [run]))


class ReviewRequestTests(unittest.TestCase):
    HEAD = "0123456789abcdef0123456789abcdef01234567"

    def test_direct_and_workflow_events_resolve_pull_request(self):
        self.assertEqual(7, fan_in.event_pr_number({"pull_request": {"number": 7}}))
        self.assertEqual(8, fan_in.event_pr_number({"workflow_run": {"pull_requests": [{"number": 8}]}}))
        self.assertEqual(9, fan_in.event_pr_number({"inputs": {"pr_number": "9"}}))

    def test_stale_workflow_event_is_rejected(self):
        self.assertTrue(fan_in.stale_workflow_event(
            {"workflow_run": {"head_sha": "old"}}, self.HEAD
        ))
        self.assertFalse(fan_in.stale_workflow_event({"pull_request": {}}, self.HEAD))

    def test_sha_marker_deduplicates_request(self):
        comments = [{"body": fan_in.MARKER.format(sha=self.HEAD), "user": {"id": 1}}]
        self.assertTrue(fan_in.review_already_requested(comments, [], self.HEAD, 1))

    def test_untrusted_sha_marker_does_not_suppress_request(self):
        comments = [{"body": fan_in.MARKER.format(sha=self.HEAD), "user": {"id": 1}}]
        self.assertFalse(fan_in.review_already_requested(comments, [], self.HEAD, 2))

    def test_exact_codex_review_deduplicates_request(self):
        reviews = [{"commit_id": self.HEAD, "user": {"id": fan_in.CODEX_USER_ID}}]
        self.assertTrue(fan_in.review_already_requested([], reviews, self.HEAD, 1))

    def test_unrelated_comment_does_not_suppress_request(self):
        self.assertFalse(fan_in.review_already_requested([{"body": "@codex review"}], [], self.HEAD, 1))

    def test_workflow_query_includes_recovery_runs(self):
        path = fan_in.workflow_runs_path(self.HEAD)
        self.assertIn(f"head_sha={self.HEAD}", path)
        self.assertNotIn("event=", path)


if __name__ == "__main__":
    unittest.main()
