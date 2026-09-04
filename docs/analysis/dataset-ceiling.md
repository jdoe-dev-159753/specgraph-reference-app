# Synthetic dataset ceiling and irreducible limits

Generated deterministically by `scripts/analyze_dataset_ceiling.py` from
`backend/src/main/resources/db/migration/V2__seed_deterministic_financial_scenarios.sql`.

## Verdict

The fixture supports deterministic adapter and workflow demonstrations. It does **not** support a
defensible numerical estimate of out-of-sample detector performance or Bayes error. There are no
verified outcome labels, only four independent customer scenarios, and the source risk rows were
hand-authored from the same scenario cues exposed as candidate features. A perfect in-sample or
oracle result would therefore measure generator leakage, not production AML validity.

## Dataset identity and inventory

- SHA-256: `e9bf396741b6cacdfbd2832ae68b7396c7dfd942802ed4332d33fbb415789e86`
- independent customer/scenario groups: 4
- transactions: 15; activity types: `{"CARD": 5, "CRYPTO": 3, "PAYMENT": 7}`
- currencies: `{"BTC": 1, "CHF": 6, "ETH": 2, "EUR": 4, "USD": 2}`; values are not converted to a common monetary unit
- non-completed transactions: 3
- source risk-assessment rows: 10 over 9 distinct transactions
- transactions per customer: `{"11111111-1111-1111-1111-111111111111": 3, "22222222-2222-2222-2222-222222222222": 3, "33333333-3333-3333-3333-333333333333": 4, "44444444-4444-4444-4444-444444444444": 5}`
- risk-assessment rows per customer: `{"11111111-1111-1111-1111-111111111111": 2, "22222222-2222-2222-2222-222222222222": 0, "33333333-3333-3333-3333-333333333333": 3, "44444444-4444-4444-4444-444444444444": 5}`

Transactions from one customer are correlated parts of one designed scenario. The effective
evaluation unit is therefore the customer/scenario, not an individual transaction.

## Candidate targets and naive baselines

| Diagnostic pseudo-target | Positive | Negative | Naive observation |
| --- | ---: | ---: | --- |
| Customer has at least one source risk row | 3 | 1 | Majority baseline 0.750; 95% Wilson range for prevalence 0.301–0.954 |
| Transaction is referenced by a source risk row | 9 | 6 | Positive share 0.600; 95% Wilson range 0.357–0.802 |

Neither pseudo-target is a verified adverse outcome. Absence of a risk row is not a demonstrated
negative, and multiple rule hits on one transaction are not independent labels.

## Split and uncertainty limits

- grouped leave-one-customer-out produces 4 folds with one test scenario each;
- only 3 of those folds retain both pseudo-label classes in training;
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
