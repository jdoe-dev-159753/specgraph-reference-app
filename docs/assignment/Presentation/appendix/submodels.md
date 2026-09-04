# Presenter appendix — landed Stage-1 submodels

This appendix is presentation-support material owned by #268. It explains analytical mechanisms that are already implemented and testable. It does not create a second architecture authority: code, SDD/ADRs and executable tests remain authoritative for implementation semantics.

## Bayesian Beta-binomial review-elevation detector

### What question does it answer?

> Given the customer's observed activities, how plausible is it that the underlying rate of **review-elevated observations** is greater than the fixed synthetic reference rate of 40%?

The detector does **not** decide whether the customer is risky, does not overwrite source `risk_assessments`, and does not produce the final advisory analysis. It emits one derived Stage-1 `RiskSignalEvidence` artifact.

Its input is the complete activity list from the application-owned `CustomerSnapshot`. Source-risk evidence is deliberately not part of this detector's feature mapping. Its output is one bounded probability score plus enough provenance to reconstruct the prior, posterior and observation counts; Stage 3 may consume that artifact later, but the detector does not synthesize a recommendation.

### What counts as a review-elevated observation?

The landed adapter maps each activity to one binary observation. An activity is elevated when at least one of these implementation rules applies:

- its status is not `completed`;
- it is a `CRYPTO` activity;
- it is a `PAYMENT` whose receiver-bank country is not `CH`.

A completed Swiss payment or completed card activity is therefore not elevated under this synthetic-demo feature family.

### Minimal mechanics

Let `p` be the unknown underlying review-elevated rate.

The implementation starts with:

`p ~ Beta(alpha=1, beta=4)`

For `n` observed activities containing `k` elevated observations:

`posterior = Beta(1 + k, 4 + n - k)`

The emitted detector score is **not** the raw ratio `k/n` and is **not** merely the posterior mean. It is:

`score = P(p > 0.40 | observed activities)`

Apache Commons Math 3.6.1 evaluates that posterior tail probability. Provenance retains the prior, posterior, reference rate, elevated/total counts, library identity and explicit synthetic-demo limitation.

### Tiny worked example: why not use the raw ratio?

Suppose the only observed activity is review-elevated.

- observations `n = 1`
- elevated observations `k = 1`
- raw ratio `k/n = 100%`
- prior `Beta(1,4)`, mean `20%`
- posterior `Beta(2,4)`, mean about `33.3%`
- emitted score `P(p > 0.40 | data) = 0.33696`, about `33.7%`

The raw ratio shouts “100%” after a single observation. The Bayesian model instead says: one positive observation should move belief upward, but one observation is weak evidence. As observations accumulate, the data increasingly dominates the prior.

### 30-second presenter explanation

> “Each activity becomes a transparent yes/no review-elevation observation. Instead of using the unstable raw percentage, I start with a deliberately conservative Beta prior and update it with the binary observations. The output is the posterior probability that the true elevation rate exceeds 40%. So one elevated event out of one does not become an absurd 100% confidence score. It is a small, explainable Stage-1 signal, not source risk truth.”

### Why this mechanism here?

- transparent enough to audit in a take-home;
- expresses uncertainty naturally when the observation count is small;
- Beta is conjugate to binary/Binomial observations, so the update is simple and deterministic;
- provenance reproduces exactly how the score was obtained.

### What would make us replace it?

Replace the synthetic prior, reference rate or binary feature mapping when representative labelled data and a defined decision objective justify a better calibrated model. A replacement must still sit behind `RiskSignalDetectorPort`, emit canonical evidence with reproducible provenance and outperform this transparent baseline on held-out evidence without weakening reviewability.

### Limitations to admit immediately

- binary feature mapping is synthetic and heuristic;
- `Beta(1,4)` prior and 40% reference rate are demonstration choices, not calibrated AML policy;
- activities are treated as observations of one bounded synthetic feature family, not a production causal model;
- no production accuracy, regulatory validity or customer-risk truth is claimed.

### Repository evidence

- implementation: [`BayesianSequentialRiskSignalDetectorAdapter`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/BayesianSequentialRiskSignalDetectorAdapter.java)
- detector identity: `beta-binomial-review-elevation-v1`
- signal identity: `posterior-review-elevation-rate`
- executable verification: [`BayesianSequentialRiskSignalDetectorAdapterTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/BayesianSequentialRiskSignalDetectorAdapterTests.java)
- architecture mapping: [`SDD.md`](../../SDD/SDD.md) and [`ADR-002`](../../ADR/ADR-002-provider-neutral-analysis.md)
- verification obligation: [`VFY-ANALYSIS-CONTRACT-001`](../../VV/VV.md)

[Diagram source](bayesian-beta-binomial.puml)

---

## Fuzzy graded review-elevation detector

### What question does it answer?

> How strongly do several transparent activity/source-risk patterns support **review elevation**, when those patterns are graded rather than binary?

The detector emits a bounded derived score in `[0,1]`. It does not modify source risk facts and it does not perform final Stage-3 synthesis.

It consumes the complete activity and source-risk collections from the application-owned `CustomerSnapshot`. It emits one canonical `RiskSignalEvidence` artifact containing the bounded graded score, normalized features, every rule activation and versioned implementation provenance. The score measures support from this synthetic rule system; it is not a calibrated probability.

### Inputs

The landed feature schema computes four normalized ratios from one `CustomerSnapshot`:

- `cryptoRatio = crypto activities / all activities`
- `crossBorderRatio = payments to non-CH receiver banks / all activities`
- `incompleteRatio = non-completed activities / all activities`
- `sourceRiskDensity = min(1, source risk evidence count / activity count)`

### Memberships and rules

Each ratio enters a bounded linear rising membership function. Current rule thresholds are:

- crypto: rises from `0.00` to full activation at `0.35`;
- cross-border: rises from `0.10` to full activation at `0.60`;
- incomplete: rises from `0.10` to full activation at `0.50`;
- source-risk density: rises from `0.10` to full activation at `0.60`;
- coupled cross-border + source-risk rule: `min(crossBorderActivation, sourceRiskActivation)`.

There is also a constant baseline activation of `0.25`.

### Defuzzification and monotonicity

The baseline rule has singleton consequent `0.05`. Every risk-positive rule has the same elevation consequent `1.0`.

The score is the weighted mean of rule consequents by their activations, then clamped to `[0,1]`.

Using one common positive consequent is deliberate: increasing the activation of any risk-positive rule cannot make the aggregate score go down. An earlier design with different positive singleton levels could violate that monotonic intuition and was corrected through review plus regression testing.

### Tiny worked example

Consider 10 activities with:

- 2 crypto activities → `cryptoRatio = 0.20` → activation about `0.571`;
- 3 cross-border payments → `crossBorderRatio = 0.30` → activation `0.400`;
- no incomplete activities → activation `0`;
- 2 source-risk evidence rows → `sourceRiskDensity = 0.20` → activation `0.200`;
- coupled cross-border + source-risk activation → `min(0.400, 0.200) = 0.200`;
- baseline activation `0.250`.

With baseline consequent `0.05` and every positive consequent `1.0`, the weighted singleton score is approximately `0.854`.

The useful part is not the specific demo number. The useful part is that every input ratio, threshold, activated rule and the defuzzification method is inspectable in provenance.

### 30-second presenter explanation

> “The fuzzy detector is graded rule inference. Instead of saying cross-border activity is either suspicious or not, each normalized feature gets a membership between zero and one. Transparent rules activate to different degrees, and a weighted singleton step produces one bounded score. Every positive rule points in the same direction, which guarantees that adding positive evidence cannot perversely lower the score.”

### Why this mechanism here?

- some review patterns are naturally graded rather than binary;
- rules and memberships stay inspectable, unlike a black-box learned score;
- the implementation surface is small enough that a project-owned inference primitive avoids a disproportionate fuzzy-runtime dependency;
- demonstrates another inference family behind the same `RiskSignalDetectorPort` without changing downstream analysis.

### What would make us replace it?

Replace the thresholds or rule surface when representative labelled evidence supports empirically validated alternatives. Replace the minimal project-owned inference primitive with a mature runtime if the rule system grows beyond this small auditable surface. Either change must preserve the port, canonical evidence, explicit versioning and regression-tested monotonic semantics.

### Limitations to admit immediately

- membership thresholds and rules are synthetic demonstration choices;
- score is not empirically calibrated as a probability;
- rule set is intentionally small and does not represent production AML policy;
- mechanism demonstrates explainable substitution, not superior predictive accuracy.

### Repository evidence

- implementation: [`FuzzyRiskSignalDetectorAdapter`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/FuzzyRiskSignalDetectorAdapter.java)
- detector identity: `graded-review-fuzzy-v1`
- rule-set version: `review-fuzzy-rules-v2`
- feature schema: `review-fuzzy-features-v1`
- executable verification: [`FuzzyRiskSignalDetectorAdapterTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/FuzzyRiskSignalDetectorAdapterTests.java)
- architecture mapping: [`SDD.md`](../../SDD/SDD.md) and [`ADR-002`](../../ADR/ADR-002-provider-neutral-analysis.md)
- verification obligation: [`VFY-ANALYSIS-CONTRACT-001`](../../VV/VV.md)

[Diagram source](fuzzy-graded-inference.puml)
