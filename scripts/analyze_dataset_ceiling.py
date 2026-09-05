#!/usr/bin/env python3
"""Inventory the synthetic review fixture without benchmarking any detector."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SQL = ROOT / "backend/src/main/resources/db/migration/V2__seed_deterministic_financial_scenarios.sql"
DEFAULT_REPORT = ROOT / "docs/analysis/dataset-ceiling.md"
INSERT = re.compile(
    r"INSERT\s+INTO\s+(?P<table>[a-z_]+)(?:\([^;]*?\))?\s+VALUES\s*(?P<values>.*?);",
    re.IGNORECASE | re.DOTALL,
)
TUPLE = re.compile(r"\((.*?)\)(?:\s*,|\s*$)", re.DOTALL)


def table_rows(sql: str, table: str) -> list[list[str]]:
    rows = []
    for statement in INSERT.finditer(sql):
        if statement.group("table").lower() != table:
            continue
        for raw in TUPLE.findall(statement.group("values")):
            values = next(csv.reader([raw], delimiter=",", quotechar="'", skipinitialspace=True))
            rows.append([value.strip() for value in values])
    if not rows:
        raise ValueError(f"missing INSERT statement for {table}")
    return rows


def wilson_interval(positive: int, total: int, z: float = 1.959963984540054) -> tuple[float, float]:
    if total <= 0:
        raise ValueError("Wilson interval requires at least one observation")
    proportion = positive / total
    denominator = 1 + z * z / total
    centre = proportion + z * z / (2 * total)
    margin = z * math.sqrt(proportion * (1 - proportion) / total + z * z / (4 * total * total))
    return (centre - margin) / denominator, (centre + margin) / denominator


def grouped_fold_audit(labels_by_customer: dict[str, list[bool]]) -> dict[str, object]:
    """Describe leave-one-customer-out class coverage without fitting a model."""
    customers = sorted(labels_by_customer)
    folds_with_both_training_classes = 0
    folds_with_both_test_classes = 0
    folds_with_both_training_and_test_classes = 0
    for test_customer in customers:
        training_labels = [
            label
            for customer in customers
            if customer != test_customer
            for label in labels_by_customer[customer]
        ]
        test_labels = labels_by_customer[test_customer]
        training_has_both_classes = len(set(training_labels)) == 2
        test_has_both_classes = len(set(test_labels)) == 2
        folds_with_both_training_classes += training_has_both_classes
        folds_with_both_test_classes += test_has_both_classes
        folds_with_both_training_and_test_classes += (
            training_has_both_classes and test_has_both_classes
        )
    return {
        "independent_customer_groups": len(customers),
        "leave_one_customer_out_folds": len(customers),
        "folds_with_both_training_classes": folds_with_both_training_classes,
        "folds_with_both_test_classes": folds_with_both_test_classes,
        "folds_with_both_training_and_test_classes": (
            folds_with_both_training_and_test_classes
        ),
        "grouped_holdout_with_both_classes_possible": (
            folds_with_both_training_and_test_classes > 0
        ),
        "independent_test_groups_per_fold": 1,
    }


def analyze(sql_path: Path = DEFAULT_SQL) -> dict[str, object]:
    sql = sql_path.read_bytes().decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    canonical_content = sql.encode("utf-8")
    transactions = table_rows(sql, "transactions")
    assessments = table_rows(sql, "risk_assessments")
    if any(len(row) != 7 for row in transactions):
        raise ValueError("transactions fixture shape changed")
    if any(len(row) != 5 for row in assessments):
        raise ValueError("risk_assessments fixture shape changed")

    by_transaction = {row[0]: row for row in transactions}
    if len(by_transaction) != len(transactions):
        raise ValueError("duplicate transaction identity")
    unknown = sorted({row[1] for row in assessments} - set(by_transaction))
    if unknown:
        raise ValueError(f"risk assessments reference unknown transactions: {unknown}")

    customers = sorted({row[1] for row in transactions})
    activity_counts = Counter(row[2] for row in transactions)
    currency_counts = Counter(row[4] for row in transactions)
    assessed_transactions = {row[1] for row in assessments}
    assessment_count_by_customer = Counter(by_transaction[row[1]][1] for row in assessments)
    activity_count_by_customer = Counter(row[1] for row in transactions)
    assessed_transaction_count_by_customer = Counter(
        by_transaction[transaction_id][1] for transaction_id in assessed_transactions
    )
    positive_customers = sum(assessment_count_by_customer[customer] > 0 for customer in customers)
    negative_customers = len(customers) - positive_customers
    customer_interval = wilson_interval(positive_customers, len(customers))
    transaction_interval = wilson_interval(len(assessed_transactions), len(transactions))
    customer_labels_by_customer = {
        customer: [assessment_count_by_customer[customer] > 0] for customer in customers
    }
    transaction_labels_by_customer = {
        customer: [row[0] in assessed_transactions for row in transactions if row[1] == customer]
        for customer in customers
    }

    return {
        "dataset_sha256": hashlib.sha256(canonical_content).hexdigest(),
        "customers": len(customers),
        "transactions": len(transactions),
        "risk_assessments": len(assessments),
        "assessed_transactions": len(assessed_transactions),
        "activity_types": dict(sorted(activity_counts.items())),
        "currencies": dict(sorted(currency_counts.items())),
        "incomplete_transactions": sum(row[5].strip().lower() != "completed" for row in transactions),
        "customer_activity_counts": dict(sorted(activity_count_by_customer.items())),
        "customer_assessment_counts": {
            customer: assessment_count_by_customer[customer] for customer in customers
        },
        "customer_pseudo_label": {
            "definition": "at least one source risk_assessment row",
            "observation_unit": "customer/scenario",
            "observations": len(customers),
            "positive": positive_customers,
            "negative": negative_customers,
            "positive_share": positive_customers / len(customers),
            "majority_baseline": max(positive_customers, negative_customers) / len(customers),
            "mechanical_wilson_95": customer_interval,
            "wilson_iid_assumption_satisfied": False,
            "split_audit": grouped_fold_audit(customer_labels_by_customer),
        },
        "transaction_pseudo_label": {
            "definition": "transaction referenced by at least one source risk_assessment row",
            "observation_unit": "transaction row clustered within customer/scenario",
            "observations": len(transactions),
            "positive": len(assessed_transactions),
            "negative": len(transactions) - len(assessed_transactions),
            "positive_share": len(assessed_transactions) / len(transactions),
            "majority_baseline": max(
                len(assessed_transactions), len(transactions) - len(assessed_transactions)
            )
            / len(transactions),
            "mechanical_wilson_95": transaction_interval,
            "wilson_iid_assumption_satisfied": False,
            "positive_by_customer": {
                customer: assessed_transaction_count_by_customer[customer]
                for customer in customers
            },
            "negative_by_customer": {
                customer: activity_count_by_customer[customer]
                - assessed_transaction_count_by_customer[customer]
                for customer in customers
            },
            "split_audit": grouped_fold_audit(transaction_labels_by_customer),
        },
    }


def render_markdown(result: dict[str, object]) -> str:
    customer = result["customer_pseudo_label"]
    transaction = result["transaction_pseudo_label"]
    customer_split = customer["split_audit"]
    transaction_split = transaction["split_audit"]
    customer_low, customer_high = customer["mechanical_wilson_95"]
    transaction_low, transaction_high = transaction["mechanical_wilson_95"]
    return f"""# Synthetic dataset ceiling and irreducible limits

Generated deterministically by `scripts/analyze_dataset_ceiling.py` from
`backend/src/main/resources/db/migration/V2__seed_deterministic_financial_scenarios.sql`.

## Verdict

The fixture supports deterministic adapter and workflow demonstrations. It does **not** support a
defensible numerical estimate of out-of-sample detector performance or Bayes error. There are no
verified outcome labels and only four non-overlapping customer scenarios. A reading of the fixture
design comments and constructed rows indicates that source risk rows were hand-authored from the
same scenario cues exposed as candidate features; this is a design interpretation, not a
mechanically verified causal relationship. A perfect in-sample or oracle result would therefore
measure generator leakage, not production AML validity.

## Dataset identity and inventory

- SHA-256: `{result['dataset_sha256']}`
- independent customer/scenario groups: {result['customers']}
- transactions: {result['transactions']}; activity types: `{json.dumps(result['activity_types'], sort_keys=True)}`
- currencies: `{json.dumps(result['currencies'], sort_keys=True)}`; values are not converted to a common monetary unit
- non-completed transactions: {result['incomplete_transactions']}
- source risk-assessment rows: {result['risk_assessments']} over {result['assessed_transactions']} distinct transactions
- transactions per customer: `{json.dumps(result['customer_activity_counts'], sort_keys=True)}`
- risk-assessment rows per customer: `{json.dumps(result['customer_assessment_counts'], sort_keys=True)}`

Transactions from one customer are correlated parts of one designed scenario. The effective
evaluation unit is therefore the customer/scenario, not an individual transaction.

## Customer pseudo-target: customer has at least one source risk row

### Customer-level statistics

- observation unit: customer/scenario;
- observations: {customer['observations']} ({customer['positive']} positive, {customer['negative']} negative; positive share {customer['positive_share']:.3f});
- naive majority baseline: {customer['majority_baseline']:.3f};
- mechanical 95% Wilson range: {customer_low:.3f}–{customer_high:.3f};
- leave-one-customer-out: {customer_split['leave_one_customer_out_folds']} folds, {customer_split['folds_with_both_training_classes']} with both classes in training and {customer_split['folds_with_both_test_classes']} with both classes in test.

### Customer-level conclusion

This pseudo-target has only four independent observations and only one negative scenario. A grouped
holdout with both classes in both train and test is **not possible**. Every test fold contains one
customer and one observed customer-level class. Per-fold ROC-AUC, PR-AUC and ranking metrics are undefined
and must not be averaged across folds. Its Wilson range is only a mechanical calculation:
four designed synthetic customers do not satisfy the iid assumption and cannot support a
prevalence, uncertainty, calibration or detector-performance claim.

## Transaction pseudo-target: transaction is referenced by a source risk row

### Transaction-level statistics

- observation unit: transaction row, clustered within customer/scenario;
- observations: {transaction['observations']} ({transaction['positive']} positive, {transaction['negative']} negative; positive share {transaction['positive_share']:.3f});
- naive majority baseline: {transaction['majority_baseline']:.3f};
- mechanical 95% Wilson range: {transaction_low:.3f}–{transaction_high:.3f};
- positive transactions per customer: `{json.dumps(transaction['positive_by_customer'], sort_keys=True)}`;
- negative transactions per customer: `{json.dumps(transaction['negative_by_customer'], sort_keys=True)}`;
- leave-one-customer-out: {transaction_split['leave_one_customer_out_folds']} folds, {transaction_split['folds_with_both_training_classes']} with both classes in training and {transaction_split['folds_with_both_test_classes']} with both classes in test.

### Transaction-level conclusion

Although some grouped folds contain both transaction pseudo-classes, they still test on exactly one
designed customer scenario. The 15 rows are therefore not 15 independent observations. A random
transaction split would leak scenario identity, while leave-one-customer-out has only four possible
test groups and cannot estimate population generalization. The transaction Wilson range likewise
uses an unsupported iid assumption. These target-specific ranges are not defensible prevalence estimates
or population confidence intervals.

For both targets, absence of a risk row is not a demonstrated negative and multiple rule hits on one
transaction are not independent labels. Neither pseudo-target is a verified adverse AML outcome.
The fixture has one short synthetic time window and no later outcome labels, so temporal, drift and
concept-performance validation are unavailable. No normalized counterparty graph or inter-customer
edges exist for a graph-level performance claim.

## Leakage and irreducible ambiguity

- Using `risk_assessments`, rule identity, score contribution, or their aggregates as predictors of
  either pseudo-target is direct target leakage and yields a tautological 1.0 oracle ceiling.
- The fixture design comments and constructed rows appear to use activity type, cross-border
  country, completion status and volume to distinguish the scenarios. Treating separation by those
  cues as generator recognition is a design reading, not a mechanically verified relationship.
- Amounts span CHF, EUR, USD, BTC and ETH without an exchange-rate observation time. Cross-currency
  magnitude comparisons are undefined rather than noisy measurements.
- Missing customer history, account tenure, counterparty history, device/channel context, verified
  outcomes and independently adjudicated labels prevent estimation of real false-positive and
  false-negative rates.

## Consequence for #128

Detector implementations may be compared for contract compliance, reproducibility, provenance,
boundedness and explanatory mechanics. Their outputs must not be described as calibrated
probabilities or evidence of production AML performance. A classical-ML benchmark requires a new,
independently justified labelled evaluation dataset with at least two groups per class for even a
minimal stratified grouped holdout; materially more groups and later labels are required for useful
uncertainty, calibration or drift conclusions. With four synthetic customers and no AML ground truth,
an honest performance benchmark is impossible. Until then, the honest practical performance
ceiling is **not numerically identifiable from this fixture**.
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sql", type=Path, default=DEFAULT_SQL)
    parser.add_argument("--output", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = analyze(args.sql)
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    rendered = render_markdown(result)
    if args.check:
        if not args.output.exists() or args.output.read_text(encoding="utf-8") != rendered:
            raise SystemExit(f"stale dataset-ceiling report: regenerate {args.output}")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8", newline="\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
