#!/usr/bin/env python3

from datetime import datetime, timezone
import unittest

import work_graph_guard as guard


class ReviewFreshnessTests(unittest.TestCase):
    def test_pull_request_event_resolves_pr_number(self):
        self.assertEqual(42, guard.event_pr_number_from_payload({"number": 42, "pull_request": {}}))

    def test_issue_comment_event_on_pr_resolves_pr_number(self):
        self.assertEqual(
            43,
            guard.event_pr_number_from_payload({"issue": {"number": 43, "pull_request": {"url": "pr"}}}),
        )

    def test_current_head_review_request_is_accepted(self):
        head_time = datetime(2026, 9, 1, 5, 0, tzinfo=timezone.utc)
        comments = [
            {"body": "@codex review\nPlease inspect this head.", "created_at": "2026-09-01T05:01:00Z"}
        ]
        self.assertTrue(guard.has_fresh_codex_request(comments, head_time))

    def test_pre_head_review_request_is_rejected(self):
        head_time = datetime(2026, 9, 1, 5, 0, tzinfo=timezone.utc)
        comments = [
            {"body": "@codex review", "created_at": "2026-09-01T04:59:59Z"},
            {"body": "Codex review", "created_at": "2026-09-01T05:02:00Z"},
        ]
        self.assertFalse(guard.has_fresh_codex_request(comments, head_time))


if __name__ == "__main__":
    unittest.main()
