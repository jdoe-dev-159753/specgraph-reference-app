# Current design map

This directory is the current application-design authority derived from `CAA-SRS-001`. It is intentionally a compact map rather than a monolithic narrative SDD.

## Authority model

- `../SRS/SRS.md` owns normative requirements, invariants, assumptions and acceptance criteria.
- `../SRS/requirements.yaml` owns machine-readable requirement identity/provenance/acceptance links.
- `../ADR/ADR-*.md` own independently reviewable architecture decisions.
- `design-map.yaml` owns the current mapping from requirements to design elements, ports, adapters, ADRs and delivery rings.
- PlantUML source in `diagrams/` is semantic design source; rendered SVG is a generated view.
- implementation and executable verification become authoritative for their concrete behaviour once introduced, but do not silently rewrite requirements or ADR rationale.

If code diverges from this map, the divergence must be reconciled by changing the design artefact/ADR or the code. A stale SDD is not permitted to remain apparently authoritative merely because Markdown is patient and never complains.

## Backend shape

The backend is one Spring Boot modular monolith with four application modules:

- `identity` owns authenticated operator context;
- `customer` owns customer lookup and activity presentation contracts;
- `risk` owns interpretation/presentation of supplied risk evidence;
- `analysis` owns analysis orchestration, provider-neutral model/policy ports and analysis history.

The dependency rule is hexagonal: project-owned domain/application contracts point outward only through project-owned ports. Spring MVC, Spring Security, JPA/Hibernate, PostgreSQL/pgvector and Spring AI remain adapters/infrastructure.

## Stable outbound ports

`CustomerActivityPort`
: loads the project-owned customer/activity/risk snapshot independently of whether data is synthetic or JPA/PostgreSQL backed.

`AnalysisModelPort`
: produces the project-owned structured analysis result independently of deterministic or live model implementation.

`PolicyKnowledgePort`
: retrieves project-owned policy evidence independently of static or vector-backed retrieval.

`AnalysisHistoryPort`
: persists and retrieves completed project-owned analyses independently of JPA/PostgreSQL details.

## Stable application contracts

`CustomerSnapshot`
: customer identity plus CARD/PAYMENT/CRYPTO activity and source-derived risk evidence.

`AnalysisResult`
: risk level, findings summary and recommendations.

`PolicyEvidence`
: retrieved text/evidence plus stable source identity/provenance needed for review.

`OperatorId`
: project-owned identity value carried into analysis/history attribution after authentication.

## Concentric delivery activation

### R0 — deployable hollow shell

R0 creates the Java/React shells, module/package topology, project-owned contracts/ports, synthetic/static adapters, deterministic model boundary and Compose deployment skeleton. The purpose is to prove that the architecture can start and that adapters are replaceable before persistence or provider integration begins.

### R1 — first authenticated visible read slice

R1 activates the first acceptance-bearing user-visible path:

1. authenticate as one of at least two seeded demo operators;
2. enter a seeded Customer ID in the React UI;
3. call the protected backend customer read interface;
4. route through `identity`, `customer` and `risk` application behaviour;
5. load a `CustomerSnapshot` through `CustomerActivityPort`;
6. satisfy the port with `SyntheticActivityAdapter`;
7. display CARD, PAYMENT and CRYPTO activity plus associated persisted-style risk evidence;
8. return an explicit not-found result for an unknown Customer ID;
9. reject or redirect unauthenticated access to protected capabilities.

R1 is the first acceptance-bearing slice for `FR-CUST-001`, `FR-ACT-001`, `FR-ACT-002`, `FR-RISK-001`, `FR-AUTH-001`, `NFR-SEC-001`, `INV-DATA-001`, `INV-DATA-002`, `INV-RISK-001`, `AC-CUST-001`, `AC-CUST-002`, `AC-ACT-001`, `AC-ACT-002`, `AC-RISK-001`, `AC-AUTH-001` and `AC-SEC-001`.

### R2 — production-like relational read path

Replace the synthetic activity adapter with the JPA/PostgreSQL adapter, Flyway schema and seeded relational data without changing `CustomerActivityPort` or `CustomerSnapshot`.

### R3 — deterministic analysis, history and full offline baseline

Activate analysis orchestration through `AnalysisModelPort` and `AnalysisHistoryPort`, initially with deterministic model output and PostgreSQL-backed history. R3 is the earliest ring at which the clean-checkout read-and-analysis demonstration and the mandatory offline verification baseline can actually be accepted, so `NFR-REP-001` and `NFR-VER-001` first become acceptance-bearing here rather than at the hollow R0 shell.

### R4 — policy retrieval and optional live-model integration

Activate `PolicyKnowledgePort` with pgvector and, optionally, a configured live `SpringAiAnalysisAdapter`. Static/deterministic adapters remain available for offline verification. Authentication is already active from R1 because protected customer and history flows require an operator context before R4.

### R5 — hardening and demo polish

Exercise failure paths, E2E verification, documentation, readiness/health and final demo ergonomics without changing the architecture merely to improve screenshots.

## Deferred design details

The SRS intentionally preserves source ambiguities. The current map therefore does not pretend the following are settled earlier than needed:

- local demo credential storage/seed mechanism beyond distinct authenticated operators, needed by R1;
- exact analysis-history relational columns beyond `INV-HIST-001`, needed by R3;
- policy chunking/embedding/ranking thresholds, needed by R4;
- optional live-model provider/model selection, which is never mandatory for acceptance.

These are bounded later-ring decisions, not excuses to postpone behaviour whose acceptance criteria already require it.

## Diagrams

- [`diagrams/component-topology.puml`](diagrams/component-topology.puml) is the semantic component/topology source.
- [`diagrams/component-topology.svg`](diagrams/component-topology.svg) is its rendered view.

The complete machine-readable requirement-to-design mapping is in [`design-map.yaml`](design-map.yaml).
