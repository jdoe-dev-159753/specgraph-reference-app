# Software Design Description (SDD) — Customer Activity Analytics

**Document ID:** `CAA-SDD-001`  
**Design map:** [`design-map.yaml`](design-map.yaml)  
**Normative requirements:** [`../SRS/SRS.md`](../SRS/SRS.md)  
**Architecture decisions:** [`../ADR/`](../ADR/)  
**Verification strategy:** `CAA-VV-001` is introduced by the dependent V&V work and is linked from this document once both authorities coexist on `main`.

This document is the canonical human-readable Software Design Description for the reference application. It is intended to be read end-to-end. `design-map.yaml` remains the machine-readable requirement-to-design mapping, while PlantUML files in `diagrams/` are the semantic diagram sources and their SVG files are generated views embedded below.

## System context and architectural orientation

The system is an operator-facing Customer Activity Analytics application. A human operator uses a browser UI to authenticate, locate a customer, inspect customer activity and risk evidence, request a grounded analysis, and later review persisted analysis history. The application is intentionally delivered as a modular monolith rather than a distributed system: browser/UI, Spring Boot application modules, PostgreSQL/pgvector persistence, and an optional external AI provider are the runtime boundaries that matter.

At the highest level, the architecture therefore has four concerns before any backend package detail matters:

1. **Operator interaction:** a browser-based React UI exposes customer review and analysis workflows.
2. **Application boundary:** a protected HTTP/JSON API mediates all operator-facing capabilities.
3. **Domain/application core:** project-owned contracts and ports define customer/activity, risk, policy grounding, analysis, and history behavior independently of infrastructure choices.
4. **Infrastructure adapters:** synthetic/JPA activity adapters, static/pgvector policy adapters, deterministic/live model adapters, history persistence, and authentication infrastructure implement those ports without becoming the application model.

The remaining figures progressively zoom from this system-level view into package structure, component interfaces, contracts, persistence, runtime interaction, failure behavior, and deployment communication.

## Authority model

- `../SRS/SRS.md` owns normative requirements, invariants, assumptions and acceptance criteria.
- `../SRS/requirements.yaml` owns machine-readable requirement identity/provenance/acceptance links.
- `../ADR/ADR-*.md` own independently reviewable architecture decisions.
- `design-map.yaml` owns the current mapping from requirements to design elements, ports, adapters, ADRs and delivery rings.
- PlantUML source in `diagrams/` is semantic design source; rendered SVG is a generated view.
- implementation and executable verification become authoritative for their concrete behaviour once introduced, but do not silently rewrite requirements or ADR rationale.

If code diverges from this map, the divergence must be reconciled by changing the design artefact/ADR or the code. A stale SDD is not permitted to remain apparently authoritative merely because Markdown is patient and never complains.

## Backend shape

The backend is one Spring Boot modular monolith with four application modules: `identity`, `customer`, `risk`, and `analysis`. Hexagonal dependency direction is strict: project-owned domain/application contracts point outward only through project-owned ports; Spring MVC, Spring Security, JPA/Hibernate, PostgreSQL/pgvector and Spring AI remain adapters/infrastructure.

![Figure 1 — Package and module boundaries](diagrams/package-modules.svg)

**Figure 1 — Package and module boundaries.** Application modules and their dependency direction inside the modular monolith. Cross-module behavior uses explicit project-owned application contracts; framework and persistence concerns remain outside the core modules.

[PlantUML source](diagrams/package-modules.puml)

## Components and interfaces

The component view makes provided/required seams explicit. The web client requires the protected HTTP/JSON surface. Inside the backend, application modules require project-owned outbound ports (`CustomerActivityPort`, `AnalysisModelPort`, `PolicyKnowledgePort`, `AnalysisHistoryPort`); replaceable adapters provide those ports.

![Figure 2 — Component topology and application interfaces](diagrams/component-topology.svg)

**Figure 2 — Component topology and application interfaces.** Provided and required interfaces across the React client, protected HTTP boundary, application modules, and replaceable outbound adapters. Arrows indicate architectural dependency, not network transport unless explicitly labelled.

[PlantUML source](diagrams/component-topology.puml)

The stable application contracts are `CustomerSnapshot`, project-owned activity/risk projections, `AnalysisResult`, `PolicyEvidence`, `OperatorId`, `AnalysisHistoryCreateCommand`, and `AnalysisHistoryEntry`. The class/domain view deliberately distinguishes those contracts from source-schema concepts; source/JPA relation classes are mapped by adapters rather than becoming members of `CustomerSnapshot`.

![Figure 3 — Project-owned domain contracts and source mappings](diagrams/domain-contracts.svg)

**Figure 3 — Project-owned domain contracts and source mappings.** Stable application contracts are separated from source-schema relation concepts. Adapter mappings convert source persistence shapes into project-owned activity, risk, analysis, and history representations.

[PlantUML source](diagrams/domain-contracts.puml)

## Relational persistence

The relational view separates source-schema facts from the narrow project-owned persistence needed by the reference application. R2 activates PostgreSQL behind `CustomerActivityPort`; R3 adds analysis history; R4 activates pgvector policy retrieval. Exact source DDL names that are not retained in the repository are not invented by the SDD.

![Figure 4 — Relational persistence model](diagrams/relational-schema.svg)

**Figure 4 — Relational persistence model.** Source activity/risk relations and the narrowly justified project-owned customer, history, and policy-vector persistence extensions. The figure distinguishes source facts from project-owned additions rather than implying one giant application aggregate.

[PlantUML source](diagrams/relational-schema.puml)

The selected relational read path is singular: `CustomerActivityPort` is implemented by `JpaCustomerActivityAdapter`, which maps source customer/activity/risk relations into project-owned `CustomerSnapshot`, `ActivityProjection`, and `RiskEvidence` contracts. The `risk` application module does not introduce a competing persistence adapter for the same source risk evidence.

## Customer review runtime

The customer-review sequence covers the authenticated operator path from React through the HTTP boundary and application contracts to either the R1 deterministic synthetic adapter or the R2+ JPA/PostgreSQL adapter. Authentication rejection and unknown-customer behavior remain explicit.

![Figure 5 — Authenticated customer review sequence](diagrams/sequence-customer-review.svg)

**Figure 5 — Authenticated customer review sequence.** Operator authentication, customer lookup, activity/risk loading through `CustomerActivityPort`, adapter substitution between deterministic and relational paths, plus explicit unauthorized and not-found outcomes.

[Open full-size SVG](diagrams/sequence-customer-review.svg) · [PlantUML source](diagrams/sequence-customer-review.puml)

## Grounded analysis runtime

Analysis first loads the customer context through `CustomerActivityPort`, then obtains policy evidence behind `PolicyKnowledgePort`, runs a deterministic or configured live model behind `AnalysisModelPort`, validates the structured result, and persists through `AnalysisHistoryPort`. Local application/port dispatch remains in-process; JDBC and HTTPS appear only across their real infrastructure boundaries.

No relevant policy evidence terminates the successfully-grounded flow with an explicit insufficient-grounding result. Model/provider failure, invalid structured output, and persistence failure likewise terminate explicitly and cannot fall through into completed/retained history.

![Figure 6 — Grounded analysis sequence](diagrams/sequence-analysis.svg)

**Figure 6 — Grounded analysis sequence.** End-to-end synchronous analysis orchestration from customer context through policy grounding, deterministic/live model substitution, result validation, and persisted history. Failure branches terminate before successful completion and are expanded separately in Figure 8.

[Open full-size SVG](diagrams/sequence-analysis.svg) · [PlantUML source](diagrams/sequence-analysis.puml)

## Analysis history review

Authenticated operators can list/inspect prior analyses through `AnalysisHistoryPort`. The read contract is `AnalysisHistoryEntry`, which carries analysis/customer identity, generating operator, generation time, structured result and evidence provenance required by `AC-HIST-002`. `AnalysisHistoryCreateCommand` is the separate write input and intentionally lacks the generated analysis identity.

![Figure 7 — Analysis history review sequence](diagrams/sequence-analysis-history.svg)

**Figure 7 — Analysis history review sequence.** Authenticated read path for listing and inspecting prior analyses while preserving the project-owned `AnalysisHistoryEntry` contract across the persistence boundary.

[Open full-size SVG](diagrams/sequence-analysis-history.svg) · [PlantUML source](diagrams/sequence-analysis-history.puml)

## Failure and degraded behavior

The focused failure view makes the negative paths independently reviewable: authentication rejection, insufficient grounding, model/provider failure, invalid structured output, and persistence failure. None of those paths may be represented as successfully completed/retained analysis.

![Figure 8 — Failure and degraded analysis behavior](diagrams/sequence-failure-modes.svg)

**Figure 8 — Failure and degraded analysis behavior.** Negative-path semantics for authentication, grounding, provider/model execution, structured-result validation, and history persistence. The figure exists separately from the happy-path sequence so failure semantics remain readable and independently reviewable.

[Open full-size SVG](diagrams/sequence-failure-modes.svg) · [PlantUML source](diagrams/sequence-failure-modes.puml)

## Deployment and communication topology

The runtime remains a modular monolith, not a static box. The deployment view distinguishes browser, web/frontend service, Spring Boot API process/container, PostgreSQL/pgvector and the optional external AI provider.

![Figure 9 — Deployment and communication topology](diagrams/deployment-topology.svg)

**Figure 9 — Deployment and communication topology.** Runtime processes/services and the communication boundaries between browser, frontend, Spring Boot API, PostgreSQL/pgvector, and optional external AI provider. Protocol labels describe actual transport boundaries; in-process module/port calls are not drawn as network links.

[PlantUML source](diagrams/deployment-topology.puml)

Communication semantics are explicit where known:

- browser ↔ web edge: HTTP locally, HTTPS for the remote demo;
- web ↔ Spring Boot API: HTTP/JSON;
- Spring Boot ↔ PostgreSQL/pgvector: JDBC/PostgreSQL protocol over private TCP;
- Spring Boot ↔ optional external AI provider: HTTPS provider API;
- module/port interactions within the modular monolith: in-process calls.

No WebSocket, event broker, FIFO, Redis, separate identity service or other transport/process is introduced merely to make the architecture look more distributed.

## Concentric delivery activation

The design is activated through concentric rings rather than parallel throwaway architectures:

- **R0 — hollow shell:** Java/Spring/React deployment shell, application modules, project-owned contracts and replaceable ports/adapters;
- **R1 — authenticated synthetic read slice:** protected customer/activity/risk path with deterministic synthetic data;
- **R2 — relational read slice:** substitute JPA/PostgreSQL/Flyway/Testcontainers behind `CustomerActivityPort`;
- **R3 — deterministic analysis/history:** static grounding, deterministic analysis and persistent reviewable history;
- **R4 — grounded provider path:** pgvector retrieval and optional live model adapter behind the existing ports;
- **R5 — hardening/demo:** reliability, observability and demo polish without changing the established boundaries.

A later ring substitutes infrastructure behind stable seams. It does not introduce a second architecture merely because a more realistic adapter is available.

## ADR consistency

The current design remains consistent with the four accepted architecture decisions:

- **ADR-001:** modular monolith with strict hexagonal boundaries;
- **ADR-002:** provider-neutral analysis/policy ports with deterministic baseline adapters;
- **ADR-003:** PostgreSQL + pgvector as the unified production-like persistence/vector service;
- **ADR-004:** assemble the Java/Spring/React implementation from mature ecosystem components rather than reimplementing commodity infrastructure.

The contract refinements made during design review, including the split between `AnalysisHistoryCreateCommand` and `AnalysisHistoryEntry`, refine those decisions rather than introducing a new architectural decision. No synthetic ADR is added merely to record normal design elaboration.

## Review criterion

A reviewer should be able to answer from this document, without reconstructing PR diffs:

- what system and operator context the architecture serves;
- what the static module/component boundaries are;
- which contracts and ports are application-owned;
- how source data maps into those contracts;
- how the principal successful and failure flows execute;
- where each network or persistence protocol actually occurs;
- how the deployment is composed;
- how later delivery rings replace adapters without changing the architecture;
- which accepted ADR explains each major design choice.
