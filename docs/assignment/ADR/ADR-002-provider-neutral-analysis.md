# ADR-002 - Provider-neutral analysis and the detection/explanation trust boundary

**Decision date:** 2026-08-30  
**Decision owner:** analysis boundary  
**Normative inputs:** `CAA-SRS-001`, `INV-AI-001`, `INV-RISK-001`, `CON-AI-001`, `CON-AI-002`, inception reuse/pattern blueprint

## Context

The assignment requires AI-assisted analysis and RAG but permits a deterministic implementation instead of a live LLM. The supplied source model also contains explicit persisted risk assessments. Those two facts create a trust-boundary question that must be answered before implementation inertia turns a language model into an unreviewable risk oracle.

The system therefore separates three concepts that may all contribute to a reviewer decision but do not have equal authority:

1. **source risk evidence**, persisted as source-shaped `risk_assessments` and treated as input fact;
2. **derived detector signals**, which may later be produced by deterministic/statistical/probabilistic/graph/classical-ML adapters when evidence justifies them;
3. **analysis/explanation**, which synthesizes customer context, risk evidence and retrieved policy into the project-owned structured result.

The same structured analysis contract must remain usable for deterministic tests, offline demonstrations and optional live-provider experiments.

## Decision

The application owns provider-neutral outbound ports:

- `AnalysisModelPort` for producing the structured analysis;
- `PolicyKnowledgePort` for retrieving relevant policy/knowledge evidence;
- `AnalysisHistoryPort` for retaining only validated completed results and their provenance.

The R3 baseline implementations are:

- `DeterministicAnalysisAdapter` behind `AnalysisModelPort`;
- `StaticPolicyAdapter` behind `PolicyKnowledgePort`;
- `JdbcAnalysisHistoryAdapter` behind `AnalysisHistoryPort`.

Later adapters may include:

- `SpringAiAnalysisAdapter` for an explicitly configured external or local model provider;
- `PgVectorPolicyAdapter` for vector retrieval from PostgreSQL/pgvector.

All model adapters return the same project-owned structured result: `LOW | MEDIUM | HIGH`, findings summary and recommendations. Retrieved evidence uses project-owned provenance metadata rather than provider SDK types.

External-provider configuration is opt-in. The default execution and verification path makes no external model call and transmits no customer/activity/policy content outside the local system.

## Detection versus explanation boundary

Persisted source risk evidence remains source truth. Neither generated prose nor a model score may be written back as if it were a source `risk_assessment`.

If a non-LLM detector is later justified, it must sit behind a project-owned outbound seam such as `RiskSignalDetectorPort`. Its output must be a separate project-owned derived-signal value carrying detector identity/version and evidence provenance. Library/runtime types stop at the adapter boundary. Activating such a detector does not change `CustomerActivityPort`, source risk rows, HTTP contracts or the analysis orchestration boundary.

The LLM/live-model role is advisory synthesis and explanation. It may combine source evidence, separately identified derived signals and retrieved policy; it does not become the sole detector or authority for customer risk. Unsupported, ungrounded or structurally invalid model output fails explicitly. A failed persistence operation cannot be represented as retained history.

The application service/facade owns orchestration and validation. Consequential interpretation remains visible as an operator/reviewer responsibility rather than being hidden behind an opaque generated score.

## Pattern mapping

- **Strategy:** `AnalysisModelPort` defines interchangeable analysis execution; `PolicyKnowledgePort` defines interchangeable knowledge retrieval. A future `RiskSignalDetectorPort` would apply the same role to derived detection without changing the application use case.
- **Adapter:** `DeterministicAnalysisAdapter`, `SpringAiAnalysisAdapter`, `StaticPolicyAdapter`, `PgVectorPolicyAdapter`, and any future detector implementation translate concrete technologies into project-owned contracts.
- **Facade / application service:** `AnalysisService` coordinates customer context, policy retrieval, model strategy execution, structured-result validation and history persistence. Provider/library orchestration does not leak into HTTP/UI code.

Hexagonal architecture is the dependency style containing those pattern roles; it is not itself a Strategy pattern.

## Candidate detector families

No candidate is implemented merely to decorate the architecture. The data and verification evidence must justify activation.

| Candidate | Potential value | Principal acceptance concerns |
| --- | --- | --- |
| supplied persisted risk assessments / explicit rules | strongest deterministic baseline and direct auditability | rule coverage, threshold provenance, false positives |
| simple statistical anomaly detection | low-cost deviation detection | baseline window, scale sensitivity, calibration |
| probabilistic/Bayesian sequential models | explicit uncertainty and temporal updating | priors, calibration, explainability, sparse evidence |
| graph/network anomaly methods | counterparties and money-flow topology | graph construction semantics, sparse networks, computational cost |
| supervised/unsupervised classical ML | richer multivariate detection when data supports it | labels, leakage, imbalance, drift, reproducibility |
| LLM-assisted synthesis over evidence + RAG | natural-language explanation and policy synthesis | grounding, hallucination, cost, confidentiality, structured-output validity |
| hybrid detector + grounded explanation | combines deterministic/model signal with reviewable synthesis | provenance separation and operational complexity |

Selection criteria include calibration, class imbalance, reproducibility, explainability, temporal/relational evidence, available dataset ceiling, concept/data drift, latency, cost and auditability.

If an ML detector is later activated, its model/version, feature/schema version and calibration/drift diagnostics belong to detector provenance. They do not contaminate source facts or force a particular ML framework into application contracts.

## Consequences

- mandatory R3 acceptance remains deterministic and provider-independent;
- source evidence, optional derived signals and generated explanation have explicit authority boundaries;
- live-provider integration can be added without changing application contracts;
- customer/policy data cannot leak merely because a provider dependency is present on the classpath;
- deterministic and production-like adapters exercise the same orchestration path;
- failure paths are reviewable and non-fabricating;
- future rules/Bayesian/graph/anomaly/classical-ML implementations can be swapped behind a stable project-owned seam if evidence justifies them;
- human/reviewer responsibility remains visible for consequential interpretation.

The trade-off is that provider/model-specific functionality must be translated through narrower project-owned contracts and provenance must be maintained explicitly.

## Alternatives not selected

### Let the LLM infer risk directly from transactions

Rejected because generated output would become an opaque detector with weak calibration, poor reproducibility and an unsafe path from prose to source-like risk facts.

### Direct provider SDK usage from application services

Rejected because it couples application behaviour to one provider and makes offline acceptance impossible.

### Hard-code one detector algorithm into the domain model

Rejected because the supplied dataset does not justify a universal detector choice and doing so would make later statistical, Bayesian, graph or classical-ML evidence harder to incorporate.

### Mock provider SDK classes in tests

Rejected because a mock of an external SDK does not prove the project's own stable boundary and encourages provider types to spread inward.

### Provider-specific application service variants

Rejected because separate orchestration services per provider would duplicate the use case and turn an Adapter/Strategy substitution seam into parallel application architectures.

## Requirement and invariant links

Primary links: `FR-AI-001`, `FR-AI-002`, `FR-RAG-001`, `FR-HIST-001`, `NFR-REP-001`, `NFR-VER-001`, `NFR-RES-001`, `CON-AI-001`, `CON-AI-002`, `INV-AI-001`, `INV-RISK-001`.
