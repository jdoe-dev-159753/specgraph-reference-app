import tempfile
import unittest
from pathlib import Path

from scripts import analyze_dataset_ceiling as ceiling


class DatasetCeilingTests(unittest.TestCase):
    def test_canonical_fixture_inventory_and_split_limits(self):
        result = ceiling.analyze()

        self.assertEqual(4, result["customers"])
        self.assertEqual(15, result["transactions"])
        self.assertEqual(10, result["risk_assessments"])
        self.assertEqual(9, result["assessed_transactions"])
        self.assertEqual({"CARD": 5, "CRYPTO": 3, "PAYMENT": 7}, result["activity_types"])
        self.assertEqual(3, result["incomplete_transactions"])
        self.assertEqual(3, result["customer_pseudo_label"]["positive"])
        self.assertEqual(1, result["customer_pseudo_label"]["negative"])
        self.assertEqual(3, result["split_audit"]["folds_with_both_training_classes"])
        self.assertFalse(result["split_audit"]["stratified_group_holdout_possible"])

    def test_report_keeps_performance_claims_bounded(self):
        report = ceiling.render_markdown(ceiling.analyze())

        self.assertIn("not numerically identifiable", report)
        self.assertIn("not be described as calibrated", report)
        self.assertIn("not production AML validity", report)
        self.assertIn("direct target leakage", report)

    def test_unknown_assessment_transaction_fails_closed(self):
        source = ceiling.DEFAULT_SQL.read_text(encoding="utf-8")
        changed = source.replace(
            "'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '10000000",
            "'ffffffff-ffff-ffff-ffff-ffffffffffff', '10000000",
            1,
        )
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / "fixture.sql"
            fixture.write_text(changed, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "unknown transactions"):
                ceiling.analyze(fixture)

    def test_dataset_identity_is_independent_of_checkout_line_endings(self):
        source = ceiling.DEFAULT_SQL.read_text(encoding="utf-8")
        with tempfile.TemporaryDirectory() as directory:
            lf_fixture = Path(directory) / "fixture-lf.sql"
            crlf_fixture = Path(directory) / "fixture-crlf.sql"
            lf_fixture.write_bytes(source.replace("\r\n", "\n").encode("utf-8"))
            crlf_fixture.write_bytes(source.replace("\r\n", "\n").replace("\n", "\r\n").encode("utf-8"))

            self.assertEqual(
                ceiling.analyze(lf_fixture)["dataset_sha256"],
                ceiling.analyze(crlf_fixture)["dataset_sha256"],
            )


if __name__ == "__main__":
    unittest.main()
