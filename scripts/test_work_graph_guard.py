#!/usr/bin/env python3

import unittest

from scripts import work_graph_guard as guard


class ReviewFreshnessTests(unittest.TestCase):
    def test_pull_request_event_resolves_pr_number(self):
        event = {"number": 42, "pull_request": {"number": 42}}
        self.assertEqual(42, guard.event_pr_number_from_payload(event))

    def test_non_pull_request_event_has_no_pr_number(self):
        self.assertIsNone(guard.event_pr_number_from_payload({"issue": {"number": 43}}))

    def test_current_head_codex_review_is_accepted(self):
        reviews = [
            {
                "commit_id": "abc123",
                "user": {"login": "chatgpt-codex-connector[bot]"},
            }
        ]
        self.assertTrue(guard.has_current_head_codex_review(reviews, "abc123"))

    def test_superseded_codex_review_is_rejected(self):
        reviews = [
            {
                "commit_id": "old123",
                "user": {"login": "chatgpt-codex-connector[bot]"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "new456"))

    def test_human_review_on_current_head_does_not_substitute_for_codex(self):
        reviews = [
            {
                "commit_id": "abc123",
                "user": {"login": "repository-owner"},
            }
        ]
        self.assertFalse(guard.has_current_head_codex_review(reviews, "abc123"))


if __name__ == "__main__":
    unittest.main()
