import unittest

from scripts import codex_review_fan_in as fan_in


class WorkflowSelectionTests(unittest.TestCase):
    def test_backend_change_requires_application_and_r4(self):
        self.assertEqual(
            {"application-ci", "r4-acceptance-ci"},
            fan_in.expected_workflows(["backend/src/main/java/App.java"]),
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


class GateTests(unittest.TestCase):
    def test_all_expected_successes_are_ready(self):
        state, names = fan_in.gate_state(
            {"application-ci", "r4-acceptance-ci"},
            [
                {
                    "id": 1, "name": "application-ci", "event": "pull_request",
                    "status": "completed", "conclusion": "success",
                },
                {
                    "id": 2, "name": "r4-acceptance-ci", "event": "pull_request",
                    "status": "completed", "conclusion": "success",
                },
            ],
        )
        self.assertEqual(("ready", []), (state, names))

    def test_missing_or_running_workflow_waits(self):
        state, names = fan_in.gate_state(
            {"application-ci", "r4-acceptance-ci"},
            [{
                "id": 1, "name": "application-ci", "event": "pull_request",
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
                        "status": "completed", "conclusion": "failure",
                    },
                    {
                        "id": 2, "name": "application-ci", "event": "workflow_dispatch",
                        "status": "completed", "conclusion": "success",
                    },
                ],
            ),
        )


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
        self.assertTrue(fan_in.review_already_requested(comments, [], self.HEAD))

    def test_exact_codex_review_deduplicates_request(self):
        reviews = [{"commit_id": self.HEAD, "user": {"id": fan_in.CODEX_USER_ID}}]
        self.assertTrue(fan_in.review_already_requested([], reviews, self.HEAD))

    def test_unrelated_comment_does_not_suppress_request(self):
        self.assertFalse(fan_in.review_already_requested([{"body": "@codex review"}], [], self.HEAD))


if __name__ == "__main__":
    unittest.main()
