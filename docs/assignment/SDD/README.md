# Current design map

This directory is the current application-design authority derived from `CAA-SRS-001`. `design-map.yaml` is the machine-readable mapping; the PlantUML sources and this document are the human-reviewable static and dynamic design views.

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

![Package/module boundaries](diagrams/package-modules.svg)

[PlantUML source](diagrams/package-modules.puml)

## Components and interfaces

The component view makes provided/required seams explicit. The web client requires the protected HTTP/JSON surface. Inside the backend, application modules require project-owned outbound ports (`CustomerActivityPort`, `AnalysisModelPort`, `PolicyKnowledgePort`, `AnalysisHistoryPort`); replaceable adapters provide those ports.

![Component topology and interfaces](diagrams/component-topology.svg)

[PlantUML source](diagrams/component-topology.puml)

The stable application contracts are `CustomerSnapshot`, `AnalysisResult`, `PolicyEvidence`, and `OperatorId`. The class/domain view deliberately distinguishes these project-owned contracts from source-schema concepts; relational/JPA inheritance is not imposed on the Java/domain model.

![Domain and contract view](diagrams/domain-contracts.svg)

[PlantUML source](diagrams/domain-contracts.puml)

## Relational design

The source schema remains authoritative for supplied transaction/activity/risk relations. The SDD adds only extensions permitted by accepted assumptions: a minimal `customers` anchor and analysis-history persistence sufficient for customer/operator/time/result/provenance attribution. Exact source DDL that is not present in the SRS is not reverse-invented here. The R4 policy-chunk physical schema remains deferred with `AMB-RAG-001`.

![Relational schema view](diagrams/relational-schema.svg)

[PlantUML source](diagrams/relational-schema.puml)

## Dynamic behaviour

A modular monolith is not a static system. Runtime calls inside one JVM are still ordered interactions, and process/container boundaries add transport semantics. These sequence views are therefore part of the design authority rather than optional decoration.

### Authenticated customer review

![Customer review sequence](diagrams/sequence-customer-review.svg)

[PlantUML source](diagrams/sequence-customer-review.puml)

The sequence shows the same application port with two adapter states: R1 synthetic and R2+ relational. Authentication occurs before protected behaviour; unknown customer is a first-class negative outcome.

### Analysis, grounding and persistence

![Analysis sequence](diagrams/sequence-analysis.svg)

[PlantUML source](diagrams/sequence-analysis.puml)

The stable orchestration is `CustomerActivityPort -> PolicyKnowledgePort -> AnalysisModelPort -> validation -> AnalysisHistoryPort`. Static/deterministic and pgvector/live implementations are substitutions behind those seams, not parallel application architectures.

### Failure and degraded paths

![Failure-mode sequence](diagrams/sequence-failure-modes.svg)

[PlantUML source](diagrams/sequence-failure-modes.puml)

The failure view covers authentication rejection, explicit missing grounding, model/provider failure, invalid structured result, and persistence failure. It preserves `AMB-RAG-001`: missing evidence is never fabricated, but the SDD does not silently decide an unspecified minimum-grounding policy.

## Deployment and communication topology

![Deployment topology](diagrams/deployment-topology.svg)

[PlantUML source](diagrams/deployment-topology.puml)

Baseline runtime communication is deliberately simple and explicit:

- browser to web edge: HTTP locally or HTTPS for the remote demo;
- web edge to Spring Boot API: HTTP/JSON over the Compose/private network;
- backend to PostgreSQL/pgvector: JDBC over the PostgreSQL protocol/TCP on the private network;
- backend to an external model provider: HTTPS only when the optional adapter is explicitly configured;
- application-module and port interactions inside the Spring Boot monolith: in-process calls.

No WebSocket, broker, FIFO, Redis, separate identity service, or streaming transport is claimed unless implementation evidence later creates that need. Simplicity is a design choice; pretending simple execution has no dynamics would just be refusal to draw the arrows.

## Concentric delivery activation

### R0 — deployable hollow shell

R0 creates the Java/React shells, module/package topology, project-owned contracts/ports, synthetic/static adapters, deterministic model boundary and Compose deployment skeleton.

### R1 — first authenticated visible read slice

R1 activates authentication plus Customer ID search through `CustomerActivityPort` and `SyntheticActivityAdapter`, displaying CARD/PAYMENT/CRYPTO activity and source-shaped risk evidence with explicit not-found and unauthenticated outcomes.

### R2 — production-like relational read path

Replace the synthetic activity adapter with the JPA/PostgreSQL adapter, Flyway schema and deterministic seed data without changing `CustomerActivityPort` or `CustomerSnapshot`.

### R3 — deterministic analysis, history and full offline baseline

Activate analysis orchestration through `AnalysisModelPort` and `AnalysisHistoryPort`, deterministic structured output, validation and PostgreSQL-backed history. This is the first ring that can accept the complete mandatory read-and-analysis verification baseline.

### R4 — policy retrieval and optional live-model integration

Activate `PolicyKnowledgePort` with pgvector and, optionally, a live `SpringAiAnalysisAdapter`. Static/deterministic adapters remain available for offline verification.

### R5 — hardening and demo polish

Exercise remaining failure paths, E2E verification, readiness/health and final demo ergonomics without changing architecture merely to improve screenshots.

## Deferred design details

The SRS intentionally preserves source ambiguities. The design therefore does not pretend the following are settled earlier than needed:

- exact local credential storage/seed mechanism beyond distinct authenticated operators;
- any analysis-history physical detail beyond the minimal fields permitted by `ASM-HIST-001` until the R3 migration is authored;
- policy chunking/embedding/ranking thresholds and physical vector-corpus shape;
- optional live-model provider/model selection.

The complete machine-readable requirement-to-design mapping remains in [`design-map.yaml`](design-map.yaml).
