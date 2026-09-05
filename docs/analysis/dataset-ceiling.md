# Synthetic dataset ceiling and irreducible limits

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

- SHA-256: `017090d6e97de1f54bc8276246ccf35e825d44b61bd16cab46386918d8d861d9`
- independent customer/scenario groups: 4
- transactions: 15; activity types: `{"CARD": 5, "CRYPTO": 3, "PAYMENT": 7}`
- currencies: `{"BTC": 1, "CHF": 6, "ETH": 2, "EUR": 4, "USD": 2}`; values are not converted to a common monetary unit
- non-completed transactions: 3
- source risk-assessment rows: 10 over 9 distinct transactions
- transactions per customer: `{"11111111-1111-1111-1111-111111111111": 3, "22222222-2222-2222-2222-222222222222": 3, "33333333-3333-3333-3333-333333333333": 4, "44444444-4444-4444-4444-444444444444": 5}`
- risk-assessment rows per customer: `{"11111111-1111-1111-1111-111111111111": 2, "22222222-2222-2222-2222-222222222222": 0, "33333333-3333-3333-3333-333333333333": 3, "44444444-4444-4444-4444-444444444444": 5}`

Transactions from one customer are correlated parts of one designed scenario. The effective
evaluation unit is therefore the customer/scenario, not an individual transaction.

## Customer pseudo-target: customer has at least one source risk row

### Customer-level statistics

- observation unit: customer/scenario;
- observations: 4 (3 positive, 1 negative; positive share 0.750);
- naive majority baseline: 0.750;
- mechanical 95% Wilson range: 0.301–0.954;
- leave-one-customer-out: 4 folds, 3 with both classes in training and 0 with both classes in test.

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
- observations: 15 (9 positive, 6 negative; positive share 0.600);
- naive majority baseline: 0.600;
- mechanical 95% Wilson range: 0.357–0.802;
- positive transactions per customer: `{"11111111-1111-1111-1111-111111111111": 2, "22222222-2222-2222-2222-222222222222": 0, "33333333-3333-3333-3333-333333333333": 3, "44444444-4444-4444-4444-444444444444": 4}`;
- negative transactions per customer: `{"11111111-1111-1111-1111-111111111111": 1, "22222222-2222-2222-2222-222222222222": 3, "33333333-3333-3333-3333-333333333333": 1, "44444444-4444-4444-4444-444444444444": 1}`;
- leave-one-customer-out: 4 folds, 4 with both classes in training and 3 with both classes in test.

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
