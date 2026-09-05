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
        self.assertEqual(0.75, result["customer_pseudo_label"]["positive_share"])
        self.assertFalse(result["customer_pseudo_label"]["wilson_iid_assumption_satisfied"])
        self.assertEqual(9, result["transaction_pseudo_label"]["positive"])
        self.assertEqual(6, result["transaction_pseudo_label"]["negative"])
        self.assertEqual(0.6, result["transaction_pseudo_label"]["majority_baseline"])
        self.assertFalse(result["transaction_pseudo_label"]["wilson_iid_assumption_satisfied"])

    def test_target_specific_grouped_split_limits_are_not_conflated(self):
        result = ceiling.analyze()
        customer = result["customer_pseudo_label"]
        transaction = result["transaction_pseudo_label"]

        self.assertEqual("customer/scenario", customer["observation_unit"])
        self.assertEqual(4, customer["observations"])
        self.assertEqual(3, customer["split_audit"]["folds_with_both_training_classes"])
        self.assertEqual(0, customer["split_audit"]["folds_with_both_test_classes"])
        self.assertEqual(
            0,
            customer["split_audit"]["folds_with_both_training_and_test_classes"],
        )
        self.assertFalse(
            customer["split_audit"]["grouped_holdout_with_both_classes_possible"]
        )

        self.assertEqual(
            "transaction row clustered within customer/scenario",
            transaction["observation_unit"],
        )
        self.assertEqual(15, transaction["observations"])
        self.assertEqual(4, transaction["split_audit"]["folds_with_both_training_classes"])
        self.assertEqual(3, transaction["split_audit"]["folds_with_both_test_classes"])
        self.assertEqual(
            3,
            transaction["split_audit"]["folds_with_both_training_and_test_classes"],
        )
        self.assertTrue(
            transaction["split_audit"]["grouped_holdout_with_both_classes_possible"]
        )
        self.assertEqual(
            {
                "11111111-1111-1111-1111-111111111111": 2,
                "22222222-2222-2222-2222-222222222222": 0,
                "33333333-3333-3333-3333-333333333333": 3,
                "44444444-4444-4444-4444-444444444444": 4,
            },
            transaction["positive_by_customer"],
        )

    def test_report_keeps_performance_claims_bounded(self):
        report = ceiling.render_markdown(ceiling.analyze())

        self.assertIn("not numerically identifiable", report)
        self.assertIn("not be described as calibrated", report)
        self.assertIn("not production AML validity", report)
        self.assertIn("direct target leakage", report)
        self.assertIn("unsupported iid assumption", report)
        self.assertIn("not defensible prevalence estimates", report)
        self.assertIn("ROC-AUC, PR-AUC and ranking metrics are undefined", report)
        self.assertIn("design reading, not a mechanically verified relationship", report)
        self.assertIn("## Customer pseudo-target", report)
        self.assertIn("### Customer-level conclusion", report)
        self.assertIn("## Transaction pseudo-target", report)
        self.assertIn("### Transaction-level conclusion", report)
        self.assertIn("The 15 rows are therefore not 15 independent observations", report)
        self.assertIn("four synthetic customers and no AML ground truth", report)
        self.assertIn("an honest performance benchmark is impossible", report)

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
