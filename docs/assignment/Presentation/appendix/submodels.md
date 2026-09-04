# Presenter appendix — landed analytical mechanisms

This appendix is presentation-support material owned by #268. It explains analytical mechanisms that are already implemented and testable. It does not create a second architecture authority: code, SDD/ADRs and executable tests remain authoritative for implementation semantics.

Coverage at freeze: Bayesian, fuzzy and Composite Stage-1 behavior; packaged Random Forest inference and descriptive drift evidence; local MiniLM/pgvector Stage-2 retrieval; and deterministic, OpenAI and LM Studio Stage-3 synthesis. Calibrated heterogeneous detector fusion is intentionally absent because it has not landed.

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

---

## Composite Stage-1 detector orchestration

### What question does it answer?

> How can several selected Stage-1 detectors run behind one `RiskSignalDetectorPort` while preserving each detector's evidence unchanged?

The Composite is orchestration, not a statistical model. It sends the same complete `CustomerSnapshot` to each selected detector, in configured order, and concatenates their `RiskSignalEvidence` artifacts. It does not average, normalize, calibrate or otherwise fuse their scores.

### Minimal mechanics

The typed factory accepts a bounded ordered selection of at most eight unique detector identities. A selection containing only one leaf returns that leaf directly. A multi-leaf selection creates a `CompositeRiskSignalDetector`; `NO_OP` cannot be mixed with concrete leaves.

For ordered leaves `D1 ... Dm` and snapshot `S`, the result is structural concatenation:

`Composite(S) = D1(S) ++ D2(S) ++ ... ++ Dm(S)`

If a child throws, execution stops and the application reports detector failure. There is no partial-success result hidden as a complete analysis.

### Tiny structural example

With selection `[BAYESIAN, FUZZY]`, Bayesian emits one posterior-tail artifact and fuzzy emits one graded-rule artifact. The Composite returns those two original artifacts, Bayesian first and fuzzy second. It emits no third "ensemble" score.

### 30-second presenter explanation

> “The Composite is plumbing with explicit semantics. It lets us run multiple interchangeable detectors through one application port, but it deliberately preserves their separate evidence instead of pretending their unlike scores are directly comparable. A real calibrated fusion would be a separate mechanism and is not implemented here.”

### Why this mechanism here?

- keeps Stage-1 extensible without changing `AnalysisService`;
- preserves detector-specific provenance and ordering;
- fails fast instead of silently producing incomplete evidence;
- avoids inventing calibration or fusion semantics that the demo data cannot support.

### What would make us replace it?

Keep the Composite for orchestration. Add a distinct, evidence-producing fusion component only after representative validation data supports explicit normalization/calibration and a measurable ensemble objective.

### Limitations to admit immediately

- current runtime selection registers `NO_OP`, `BAYESIAN`, `FUZZY` and `RANDOM_FOREST`;
- child scores have different meanings and must not be compared or averaged as if calibrated;
- fail-fast semantics provide no partial result when one child fails;
- no heterogeneous calibrated fusion is claimed.

### Repository evidence

- orchestration: [`CompositeRiskSignalDetector`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/CompositeRiskSignalDetector.java)
- typed selection: [`RiskSignalDetectorFactory`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/RiskSignalDetectorFactory.java) and [`RiskSignalDetectorConfiguration`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/RiskSignalDetectorConfiguration.java)
- executable verification: [`CompositeRiskSignalDetectorTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/CompositeRiskSignalDetectorTests.java)
- architecture mapping: [`SDD.md`](../../SDD/SDD.md)

[Diagram source](composite-detector-orchestration.puml)

---

## Random Forest Stage-1 packaged inference and drift diagnostic

### What question does it answer?

> For one bounded activity-derived feature vector, what share of the fixed forest's uniformly weighted trees votes for `REVIEW_ELEVATED`?

The landed runtime packages one immutable Tribuo protobuf model and its project-owned manifest. Explicit `RANDOM_FOREST` selection lazily loads and memoizes the validated adapter, which emits canonical `RiskSignalEvidence`. It never trains on a request and never consumes PII or source-risk scores.

### Inputs and minimal mechanics

The ordered feature contract is:

1. `activity-volume = min(1, activity count / 100)`;
2. `crypto-ratio = crypto activities / all activities`;
3. `cross-border-payment-ratio = non-CH payments / all activities`;
4. `incomplete-ratio = non-completed activities / all activities`.

Tribuo deserializes the manifest-pinned ensemble of 31 classification trees. The runtime and adapter verify the manifest trust anchor, bounded resource sizes, protobuf SHA-256, feature schema/domain, two output labels, tree count/depth, uniform positive weights, voting combiner and recorded trainer/library provenance before accepting the model.

With `T` uniformly weighted trees and `v` votes for `REVIEW_ELEVATED`, the emitted score is the forest vote share:

`score = v / T`

### Descriptive feature-drift diagnostic

A separate diagnostic projects a bounded observation window through the same four-feature schema and computes a two-sample Kolmogorov-Smirnov `D` statistic per feature against a SHA-256-pinned 12-row synthetic reference. Fewer than 12 observations returns `INSUFFICIENT_OBSERVATIONS`; otherwise maximum `D >= 0.50` produces an operational `REVIEW_TRIGGERED` status. The diagnostic is observability evidence only: it neither changes inference nor claims concept/performance drift.

### Tiny structural example

If 22 of the packaged model's 31 trees vote `REVIEW_ELEVATED`, the signal score is about `0.710`; the winning class is also retained in provenance. That score means “22 uniformly weighted trees voted for this class.” It does not mean a calibrated 71% probability that a customer is risky.

For drift, an observation window matching the reference feature distributions produces maximum `D = 0.0`. A maximum `D = 0.50` triggers review because the threshold is inclusive; it is not an automatic customer-risk decision or proof that detector performance changed.

### 30-second presenter explanation

> “This is a packaged fixed ensemble of 31 decision trees behind our Stage-1 port. Four bounded, non-PII activity features enter the pinned model, and the output is the share of uniformly weighted trees voting for review elevation. A separate KS diagnostic can flag large feature-distribution shifts for review. Both mechanisms are reproducible demo evidence: neither a calibrated risk probability nor proof of production performance.”

### Why this mechanism here?

- delivers a selectable learned classical-ML leaf behind the same project-owned detector contract;
- lazy fixed request-time inference avoids loading an unused model, hidden online training and mutable model state;
- packaged manifest, model and drift-reference SHA-256 checks make the executable artifacts reviewable;
- a small bounded feature schema limits accidental PII and source-risk leakage.

### What would make us replace it?

Replace the model only after a representative, versioned dataset and held-out objective justify a better candidate. Preserve the feature/evidence boundary, immutable artifact identity and reproducible comparison evidence.

### Limitations to admit immediately

- the packaged model was trained from 12 hand-authored synthetic rows whose labels are separable by construction;
- the emitted vote share is not a calibrated probability;
- the drift reference also contains only 12 hand-authored rows; KS `D` describes input shift, not concept drift, calibration or performance;
- #130 reached a no-go for numerical benchmarking on the available application fixture: four designed customer scenarios, no verified outcome labels and generator leakage make out-of-sample performance and Bayes error non-identifiable;
- no accuracy, regulatory-validity or production AML-performance claim follows from the packaged demo model.

### Repository evidence

- inference adapter: [`RandomForestRiskSignalDetectorAdapter`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/randomforest/RandomForestRiskSignalDetectorAdapter.java)
- packaged runtime: [`RandomForestRiskSignalDetectorRuntime`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/randomforest/RandomForestRiskSignalDetectorRuntime.java) and its [`canonical manifest`](../../../../backend/src/main/resources/dev/specgraph/reference/analysis/randomforest/synthetic-review-random-forest-v1.properties)
- feature contract: [`RandomForestRiskFeatures`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/randomforest/RandomForestRiskFeatures.java)
- manifest: [`RandomForestModelManifest`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/randomforest/RandomForestModelManifest.java)
- drift evidence: [`RandomForestFeatureDriftDiagnostic`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/randomforest/RandomForestFeatureDriftDiagnostic.java), [`RandomForestFeatureDriftReport`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/randomforest/RandomForestFeatureDriftReport.java) and the [`pinned reference`](../../../../backend/src/main/resources/dev/specgraph/reference/analysis/randomforest/synthetic-review-random-forest-v1-drift-reference.properties)
- executable verification: [`RandomForestRiskSignalDetectorRuntimeTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/randomforest/RandomForestRiskSignalDetectorRuntimeTests.java), [`RandomForestRiskSignalDetectorAdapterTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/randomforest/RandomForestRiskSignalDetectorAdapterTests.java) and [`RandomForestFeatureDriftDiagnosticTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/randomforest/RandomForestFeatureDriftDiagnosticTests.java)
- no-go benchmark evidence for #130: [`dataset-ceiling.md`](../../../analysis/dataset-ceiling.md)

[Diagram source](random-forest-inference.puml)

---

## Stage-2 local MiniLM and pgvector retrieval

### What question does it answer?

> Which small pieces of the local synthetic policy corpus are semantically closest to the bounded customer-activity query and can ground Stage-3 synthesis?

Stage 2 supplies context. It is neither a risk detector nor a recommendation generator. Under the `r4` profile, the application embeds policy chunks and a bounded snapshot-derived query with local `all-MiniLM-L6-v2`, then asks PostgreSQL/pgvector for the nearest policy chunks.

### Inputs and minimal mechanics

- startup loads the repository's synthetic policy index, token-splits each source into at most 20 chunks and gives each chunk a deterministic content-derived UUID;
- a local 384-dimensional MiniLM embedding model maps chunks and the query into vectors;
- pgvector stores vectors and performs HNSW cosine-distance search;
- the default request asks for `topK = 3` with similarity threshold `0.35`;
- query construction is bounded to the newest 50 activity terms, newest 20 source-risk terms and 4,000 characters.

Each accepted match becomes project-owned `PolicyEvidence` containing the stable document identity, policy text and retrieval metadata including the embedding-model identity and available similarity score.

### Tiny structural example

For a snapshot with 120 activities and 25 source-risk rows, query construction considers at most the newest 50 activities and newest 20 risk rows, then applies the 4,000-character cap. Retrieval asks for at most three sufficiently similar chunks. Those chunks enter the bounded evidence envelope; they do not themselves determine the final risk level.

### 30-second presenter explanation

> “Stage 2 is local retrieval, not the AI verdict. MiniLM turns our synthetic policy chunks and a bounded activity query into comparable vectors. pgvector returns a few nearby chunks, and we translate them into traceable policy evidence. Stage 3 can use only that bounded evidence, and the analysis fails closed if no policy grounding is available.”

### Why this mechanism here?

- semantic retrieval is more flexible than hard-coded keyword matching;
- model inference and vector storage stay inside the demo runtime boundary;
- deterministic chunk identities and metadata keep cited context traceable;
- the `PolicyKnowledgePort` prevents Spring AI and pgvector types from leaking into the use case.

### What would make us replace it?

Change the embedding model, index or retrieval parameters only after retrieval evaluation on a versioned representative corpus. A replacement must preserve bounded queries, stable evidence identity and explicit retrieval provenance.

### Limitations to admit immediately

- corpus and thresholds are synthetic demonstration choices;
- no claim of policy completeness, retrieval recall or production ranking quality is made;
- initial model artifacts come from pinned configuration URLs and are cached locally; offline execution depends on that cache being prepared;
- vector similarity supplies candidate context, not truth, causality or AML validity.

### Repository evidence

- retrieval adapter: [`PgVectorPolicyAdapter`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/PgVectorPolicyAdapter.java)
- embedding/vector configuration: [`PgVectorPolicyConfiguration`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/PgVectorPolicyConfiguration.java) and [`application-r4.yml`](../../../../backend/src/main/resources/application-r4.yml)
- deterministic corpus loading: [`SyntheticPolicyCorpusLoader`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/SyntheticPolicyCorpusLoader.java)
- executable verification: [`PgVectorPolicyAdapterTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/PgVectorPolicyAdapterTests.java) and [`PgVectorPolicyIntegrationTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/PgVectorPolicyIntegrationTests.java)
- architecture decision: [`ADR-003`](../../ADR/ADR-003-postgresql-pgvector-persistence.md)

[Diagram source](stage2-minilm-pgvector-retrieval.puml)

---

## Stage-3 deterministic, OpenAI and LM Studio backends

### What question does it answer?

> How can one bounded evidence envelope become the same project-owned advisory result through three explicitly selected synthesis strategies?

`AnalysisBackendId` selects exactly one `AnalysisModelPort` implementation: `DETERMINISTIC`, `OPENAI` or `LOCAL`. The selection is independent of Stage-1 detector choice. Every backend receives an `AnalysisEvidenceEnvelope` and must return an `AnalysisResult` plus `AnalysisModelProvenance`; the application validates evidence references before persistence.

### Backend mechanics and output semantics

| Backend | Synthesis mechanism | Transmission provenance |
|---|---|---|
| deterministic | Fixed offline rule: zero source-risk rows -> LOW, one or two -> MEDIUM, more than two -> HIGH; fixed explanatory text | `externalTransmission=false` |
| OpenAI | Spring AI chat adapter, shared grounded prompt, provider-native structured output plus local schema validation | `externalTransmission=true`, `dataPolicy=synthetic-demo-only` |
| local / LM Studio | Same grounded prompt and structured-output validation through LM Studio's OpenAI-compatible endpoint | `externalTransmission=false`, runtime `lmstudio/llama.cpp` |

`specgraph.analysis.backend` is the authoritative typed selector and defaults to `deterministic`. Unknown, mismatched or unavailable selections fail startup instead of silently falling back. The OpenAI and LM Studio leaves are materialized only when selected.

### Tiny structural example

The same envelope can contain 12 total activities, two source-risk rows, two Stage-1 artifacts and three policy chunks. The deterministic backend returns `MEDIUM` by its fixed rule. An explicitly selected model backend receives the same bounded evidence and must return the same result schema with references to that evidence. Different wording is allowed; unsupported references or invalid structure are rejected.

### 30-second presenter explanation

> “Stage 3 is an interchangeable synthesizer, not a new source of truth. The safe default is a deterministic offline rule. OpenAI is an explicit external option, and LM Studio is an explicit local OpenAI-compatible option. All three sit behind the same port, consume the same bounded evidence, return the same schema and record where processing occurred.”

### Why this mechanism here?

- gives the demo an offline deterministic baseline and two optional model-backed comparisons;
- keeps provider/runtime details behind an application-owned port;
- makes external transmission an explicit selection and recorded provenance fact;
- shared structured-output and grounding checks constrain model-backed results.

### What would make us replace it?

Replace a backend when versioned evaluation shows another implementation better satisfies the same bounded contract. Provider migration must not change evidence authority, grounding validation or the explicit transmission policy.

### Limitations to admit immediately

- outputs are advisory synthetic-demo analyses, not production decisions or regulatory evidence;
- identical contracts do not imply identical quality across backends or model versions;
- model-backed output remains stochastic/provider-dependent despite schema and grounding checks;
- LM Studio requires an explicitly configured loopback/private IP-literal endpoint; public addresses and hostnames are rejected, and compatibility depends on the selected local model/runtime;
- no backend result is claimed to be calibrated AML risk.

### Repository evidence

- typed selection: [`AnalysisBackendId`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/AnalysisBackendId.java), [`AnalysisBackendFactory`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/AnalysisBackendFactory.java) and [`AnalysisBackendConfiguration`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/AnalysisBackendConfiguration.java)
- implementations: [`DeterministicAnalysisAdapter`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/DeterministicAnalysisAdapter.java), [`SpringAiAnalysisAdapter`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/SpringAiAnalysisAdapter.java) and [`LmStudioAnalysisAdapter`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/LmStudioAnalysisAdapter.java)
- application validation: [`AnalysisService`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/AnalysisService.java) and [`AnalysisGroundingValidator`](../../../../backend/src/main/java/dev/specgraph/reference/analysis/AnalysisGroundingValidator.java)
- executable verification: [`AnalysisBackendSelectionIntegrationTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/AnalysisBackendSelectionIntegrationTests.java), [`OpenAiModelSelectionIntegrationTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/OpenAiModelSelectionIntegrationTests.java) and [`LmStudioAnalysisAdapterIntegrationTests`](../../../../backend/src/test/java/dev/specgraph/reference/analysis/LmStudioAnalysisAdapterIntegrationTests.java)
- architecture mapping: [`ADR-002`](../../ADR/ADR-002-provider-neutral-analysis.md) and [`SDD.md`](../../SDD/SDD.md)

[Diagram source](stage3-analysis-backends.puml)
