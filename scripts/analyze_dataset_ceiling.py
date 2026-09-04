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
    positive_customers = sum(assessment_count_by_customer[customer] > 0 for customer in customers)
    negative_customers = len(customers) - positive_customers
    valid_leave_one_out_folds = sum(
        positive_customers - int(assessment_count_by_customer[customer] > 0) > 0
        and negative_customers - int(assessment_count_by_customer[customer] == 0) > 0
        for customer in customers
    )
    customer_interval = wilson_interval(positive_customers, len(customers))
    transaction_interval = wilson_interval(len(assessed_transactions), len(transactions))

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
            "positive": positive_customers,
            "negative": negative_customers,
            "majority_baseline": max(positive_customers, negative_customers) / len(customers),
            "wilson_95": customer_interval,
        },
        "transaction_pseudo_label": {
            "definition": "transaction referenced by at least one source risk_assessment row",
            "positive": len(assessed_transactions),
            "negative": len(transactions) - len(assessed_transactions),
            "positive_share": len(assessed_transactions) / len(transactions),
            "wilson_95": transaction_interval,
        },
        "split_audit": {
            "independent_customer_groups": len(customers),
            "leave_one_customer_out_folds": len(customers),
            "folds_with_both_training_classes": valid_leave_one_out_folds,
            "stratified_group_holdout_possible": min(positive_customers, negative_customers) >= 2,
            "test_groups_per_leave_one_out_fold": 1,
        },
    }


def render_markdown(result: dict[str, object]) -> str:
    customer = result["customer_pseudo_label"]
    transaction = result["transaction_pseudo_label"]
    split = result["split_audit"]
    customer_low, customer_high = customer["wilson_95"]
    transaction_low, transaction_high = transaction["wilson_95"]
    return f"""# Synthetic dataset ceiling and irreducible limits

Generated deterministically by `scripts/analyze_dataset_ceiling.py` from
`backend/src/main/resources/db/migration/V2__seed_deterministic_financial_scenarios.sql`.

## Verdict

The fixture supports deterministic adapter and workflow demonstrations. It does **not** support a
defensible numerical estimate of out-of-sample detector performance or Bayes error. There are no
verified outcome labels, only four independent customer scenarios, and the source risk rows were
hand-authored from the same scenario cues exposed as candidate features. A perfect in-sample or
oracle result would therefore measure generator leakage, not production AML validity.

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

## Candidate targets and naive baselines

| Diagnostic pseudo-target | Positive | Negative | Naive observation |
| --- | ---: | ---: | --- |
| Customer has at least one source risk row | {customer['positive']} | {customer['negative']} | Majority baseline {customer['majority_baseline']:.3f}; 95% Wilson range for prevalence {customer_low:.3f}–{customer_high:.3f} |
| Transaction is referenced by a source risk row | {transaction['positive']} | {transaction['negative']} | Positive share {transaction['positive_share']:.3f}; 95% Wilson range {transaction_low:.3f}–{transaction_high:.3f} |

Neither pseudo-target is a verified adverse outcome. Absence of a risk row is not a demonstrated
negative, and multiple rule hits on one transaction are not independent labels.

## Split and uncertainty limits

- grouped leave-one-customer-out produces {split['leave_one_customer_out_folds']} folds with one test scenario each;
- only {split['folds_with_both_training_classes']} of those folds retain both pseudo-label classes in training;
- a stratified grouped holdout with both classes in train and test is **not possible**;
- transaction-level random splitting would leak customer/scenario identity across train and test;
- the fixture has one short synthetic time window and no later outcome labels, so temporal, drift and concept-performance validation are unavailable;
- no normalized counterparty graph or inter-customer edges exist for a graph-level performance claim.

## Leakage and irreducible ambiguity

- Using `risk_assessments`, rule identity, score contribution, or their aggregates as predictors of
  either pseudo-target is direct target leakage and yields a tautological 1.0 oracle ceiling.
- Activity type, cross-border country, completion status and volume were deliberately selected to
  distinguish the four scenarios; separation by those cues is generator recognition.
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
uncertainty, calibration or drift conclusions. Until then, the honest practical performance ceiling
is **not numerically identifiable from this fixture**.
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
