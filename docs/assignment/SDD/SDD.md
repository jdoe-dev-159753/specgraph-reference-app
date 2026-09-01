# Software Design Description (SDD) - Customer Activity Analytics

**Document ID:** `CAA-SDD-001`  
**Design map:** [`design-map.yaml`](design-map.yaml)  
**Normative requirements:** [`CAA-SRS-001`](../SRS/SRS.md)  
**Machine-readable requirements:** [`requirements.yaml`](../SRS/requirements.yaml)  
**Architecture decisions:** [`ADR-001`](../ADR/ADR-001-modular-monolith-hexagonal.md), [`ADR-002`](../ADR/ADR-002-provider-neutral-analysis.md), [`ADR-003`](../ADR/ADR-003-postgresql-pgvector-persistence.md), [`ADR-004`](../ADR/ADR-004-baseline-web-stack.md), [`ADR-005`](../ADR/ADR-005-prebuilt-demo-container-packaging.md), [`ADR-006`](../ADR/ADR-006-compose-oci-multi-platform-distribution.md), [`ADR-007`](../ADR/ADR-007-spring-jdbc-relational-adapters.md)  
**Verification strategy:** [`CAA-VV-001`](../VV/VV.md)

This document is the canonical human-readable design authority. [`design-map.yaml`](design-map.yaml) is its machine-readable requirement-to-design companion. PlantUML and Graphviz/DOT files in [`diagrams/`](diagrams/) are the semantic sources for rendered figures; SVGs are generated views, not separate design authorities.

## 1. System context and architectural orientation

Customer Activity Analytics is an operator-facing application for reviewing customer activity, source-shaped risk evidence, and analysis results. It is deliberately a modular monolith. Browser/UI, one Spring Boot application process, PostgreSQL, and an optional external AI provider are the only runtime boundaries that matter to the application architecture.

The architecture separates four concerns:

1. **Operator interaction:** React exposes customer review and analysis/history workflows.
2. **Inbound application boundary:** module-owned Spring MVC adapters expose coarse-grained use cases.
3. **Application/domain core:** project-owned contracts and ports define customer/activity, risk, policy, model and history behavior independently of infrastructure.
4. **Infrastructure adapters:** synthetic/Spring-JDBC activity, static/pgvector policy, deterministic/live analysis and JDBC history adapters implement those ports.

![Figure 1 - Architectural context schematic](diagrams/system-context.svg)

**Figure 1 - system boundary and external dependencies.** The optional live AI provider is not part of mandatory execution. Internal module and port calls remain in-process.

[PlantUML source](diagrams/system-context.puml)

## 2. Authority and notation

- `CAA-SRS-001` owns normative requirements, constraints, invariants and acceptance semantics.
- `requirements.yaml` owns machine-readable requirement identity and acceptance links.
- ADRs own independently reviewable architectural decisions.
- `design-map.yaml` owns the machine-readable mapping from requirements to design elements and activation rings.
- this SDD owns the human-readable architecture and behavioral synthesis.
- implementation and executable verification own concrete behavior once activated, but cannot silently rewrite the SRS or ADR rationale.

The figures use UML 2.5.1 where it answers the engineering question and explicitly labelled non-UML schematics where topology/geometry is the point:

- Figure 1: architecture context schematic;
- Figure 2a: UML Package diagram;
- Figure 2b: hexagonal ports/adapters schematic;
- Figure 3: UML Component diagram;
- Figures 4a/4b: UML Class diagrams;
- Figure 5: entity-relationship view;
- Figures 6-9: UML Activity diagrams with ActivityPartitions;
- Figure 10: UML Deployment diagram;
- Figure 11: concentric delivery-ring schematic.

## 3. Modular-monolith structure

The backend has exactly four Spring Modulith application modules under `dev.specgraph.reference`:

- `identity`: real authenticated operator context, activated in R4;
- `risk`: project-owned source-risk contracts, active from R1;
- `customer`: customer lookup and activity-review use cases, active from R1;
- `analysis`: analysis orchestration, policy evidence and analysis history, active from R3.

The current public cross-module direction required by customer review is `customer -> risk`. Analysis may use customer-facing application contracts. Reverse infrastructure dependencies are prohibited.

Spring Modulith verification ratchets the physical graph: the detected module identifiers must remain exactly `identity`, `customer`, `risk`, and `analysis`. Transport, persistence or generic helper packages do not become fifth horizontal modules.

![Figure 2a - UML Package diagram](diagrams/package-modules.svg)

[PlantUML source](diagrams/package-modules.puml)

Hexagonal dependency direction remains strict. Spring MVC, Spring Security, Spring JDBC, PostgreSQL/pgvector and provider SDKs stop at adapters. Application-owned contracts do not import them.

![Figure 2b - Hexagonal architecture](diagrams/hexagonal-architecture.svg)

[PlantUML source](diagrams/hexagonal-architecture.puml)

## 4. Ports, adapters and restrained GoF pattern use

The central outbound ports are:

| Port | Responsibility | Activated behavior |
| --- | --- | --- |
| `CustomerActivityPort` | load one project-owned `CustomerSnapshot` | synthetic R1, Spring JDBC R2+ |
| `PolicyKnowledgePort` | return project-owned `PolicyEvidence` | static deterministic evidence R3, pgvector R4 |
| `AnalysisModelPort` | produce a structured project-owned `AnalysisResult` | deterministic R3, optional live provider later |
| `AnalysisHistoryPort` | persist/list/inspect validated analysis history | Spring JDBC R3+ |

The primary adapters are `CustomerReviewHttpAdapter`, `AnalysisHttpAdapter`, `SyntheticActivityAdapter`, `JdbcCustomerActivityAdapter`, `StaticPolicyAdapter`, `PgVectorPolicyAdapter`, `DeterministicAnalysisAdapter`, `SpringAiAnalysisAdapter`, and `JdbcAnalysisHistoryAdapter`.

The inception-selected GoF roles remain intentionally limited:

- **Adapter:** translates framework/provider/storage APIs to project-owned ports.
- **Strategy:** selects interchangeable activity, policy, model or future detector behavior behind stable ports.
- **Facade/application service:** exposes one coarse-grained use case while hiding multi-port orchestration. `AnalysisService` is the R3 example.

Hexagonal architecture is the dependency style containing these roles; it is not itself a Strategy pattern. New patterns are not added to inflate vocabulary.

![Figure 3 - UML Component diagram](diagrams/component-topology.svg)

[PlantUML source](diagrams/component-topology.puml)

## 5. Project-owned contracts

Stable contracts include `CustomerSnapshot`, activity/risk projections, `AnalysisResult`, `PolicyEvidence`, `OperatorId`, `AnalysisHistoryCreateCommand`, and `AnalysisHistoryEntry`.

`AnalysisResult` is constrained to a structured risk level `LOW | MEDIUM | HIGH`, a non-empty findings summary and non-empty recommendations. `AnalysisHistoryEntry` adds generated analysis identity, customer identity, operator attribution, generation time, structured result and evidence provenance.

Persistence rows and provider response classes do not become members of these contracts.

![Figure 4a - UML Class diagram - project-owned contracts](diagrams/domain-contracts.svg)

[PlantUML source](diagrams/domain-contracts.puml)

![Figure 4b - UML Class diagram - source mapping](diagrams/source-contract-mapping.svg)

[PlantUML source](diagrams/source-contract-mapping.puml)

## 6. Detection versus explanation trust boundary

Source `risk_assessments` are persisted source-shaped evidence. They remain distinct from generated analysis.

The R3 deterministic analysis may synthesize customer context, source risk evidence and static policy evidence, but it cannot manufacture a source risk fact. A later live LLM remains advisory explanation/synthesis, not the sole detector or authority for customer risk.

If later evidence justifies deterministic/statistical/Bayesian/graph/classical-ML detection, that capability must sit behind a project-owned detector seam such as `RiskSignalDetectorPort`. Its outputs remain separately identified derived signals carrying detector/version/provenance metadata. A library-specific model type must never leak into source risk rows or application contracts.

[`ADR-002`](../ADR/ADR-002-provider-neutral-analysis.md) owns this decision and compares candidate detector families.

`CON-AI-002` is a design constraint, not merely a testing convention: **the default configuration does not transmit customer/activity/policy content to an external AI provider**. External transmission requires an explicitly selected live-provider adapter and data permitted for that provider. Merely placing a provider dependency on the classpath does not activate transmission.

## 7. Relational persistence

R2 activates PostgreSQL 17 behind `CustomerActivityPort` using Spring Framework `JdbcClient`. Flyway is the sole schema/migration authority. Explicit SQL maps source relations into project-owned projections; no ORM lifecycle competes with Flyway.

The source relation types include exact monetary `DECIMAL/NUMERIC`, bounded currency/status fields, booleans, country codes and timezone-free `TIMESTAMP`. The adapter verifies the schema contract against the migrated PostgreSQL schema and preserves monetary amounts as exact decimal values independent from currency.

Multi-query customer aggregate reads execute under PostgreSQL `REPEATABLE READ`, so activities and risk evidence cannot be assembled from different committed snapshots.

R3 adds project-owned `analysis_history` through Flyway and `JdbcAnalysisHistoryAdapter`. Only a validated analysis whose persistence succeeds is represented as completed retained history.

![Figure 5 - Relational persistence model](diagrams/relational-schema.svg)

[PlantUML source](diagrams/relational-schema.puml)

Source `TIMESTAMP` values are wall-clock values without timezone metadata. `specgraph.source-time-zone`, exposed as `SPECGRAPH_SOURCE_TIME_ZONE`, is explicit configuration. The deterministic fixture default is UTC; host JVM/OS timezone is never guessed as source semantics.

## 8. Customer review behavior

`CustomerReviewHttpAdapter -> CustomerReviewUseCase -> CustomerReviewService -> CustomerActivityPort` is the stable read path. R1 uses the synthetic adapter; R2+ substitutes `JdbcCustomerActivityAdapter` without changing the application-owned contract.

Unknown customers return an explicit not-found result rather than fabricated data.

![Figure 6 - UML Activity diagram - customer review](diagrams/activity-customer-review.svg)

[PlantUML source](diagrams/activity-customer-review.puml) | [Sequence view](diagrams/sequence-customer-review.svg)

## 9. R3 deterministic analysis and history

R3 activates the mandatory offline path:

```text
AnalysisHttpAdapter
  -> AnalysisUseCase
  -> AnalysisService
  -> CustomerActivityPort
  -> PolicyKnowledgePort
  -> AnalysisModelPort
  -> structured-result validation
  -> AnalysisHistoryPort
  -> PostgreSQL
```

The successful path is complete only after history persistence succeeds. Static policy evidence supplies deterministic grounding for R3 without claiming that R4 pgvector/RAG is already implemented.

![Figure 7 - UML Activity diagram - grounded analysis](diagrams/activity-grounded-analysis.svg)

[PlantUML source](diagrams/activity-grounded-analysis.puml) | [Orchestration sequence](diagrams/sequence-analysis.svg) | [Adapter sequence](diagrams/sequence-analysis-adapters.svg)

R3 requires operator **attribution** in persisted provenance but deliberately does not activate R4 authentication/authorization. The R3 HTTP/application boundary supplies a deterministic project-owned `OperatorId` for offline verification. R4 later replaces that deterministic attribution source with real authenticated multi-operator context without changing the history contract.

R3 list/inspect operations therefore prove persistent reviewable history, while authorization of those operations remains an R4 acceptance obligation.

![Figure 8 - UML Activity diagram - analysis history review](diagrams/activity-history-review.svg)

[PlantUML source](diagrams/activity-history-review.puml) | [Sequence view](diagrams/sequence-analysis-history.svg)

## 10. Failure and degraded behavior

`NFR-RES-001` maps explicitly to the analysis module and policy/model/history boundaries. These failure modes terminate without a false completed/history state:

- customer not found;
- insufficient policy grounding;
- policy adapter failure;
- model execution failure;
- structurally invalid model output;
- persistence failure.

No evidence means no successfully grounded model execution. Invalid structured output cannot reach history persistence. Failed persistence cannot be reported as retained history.

![Figure 9 - UML Activity diagram - failure behavior](diagrams/activity-failure-behavior.svg)

[PlantUML source](diagrams/activity-failure-behavior.puml) | [Grounding/auth sequence](diagrams/sequence-failure-grounding.svg) | [Model/validation sequence](diagrams/sequence-failure-model.svg) | [Persistence sequence](diagrams/sequence-failure-persistence.svg)

## 11. Deployment and communication topology

Each checkpoint packages built React assets and the Spring MVC API into one Spring Boot executable JAR. Embedded Tomcat serves both `/` and `/api/*` from one origin. Node/Vite and Maven are build-stage tools, not runtime services.

![Figure 10 - UML Deployment diagram](diagrams/deployment-topology.svg)

[PlantUML source](diagrams/deployment-topology.puml)

Checkpoint host ports are deliberately independent so rings can be compared without adding a reverse proxy only for presentation:

- R0: host `8080` -> container Tomcat `8080`;
- R1: host `8081` -> container Tomcat `8080`;
- R2: host `8082` -> container Tomcat `8080`, private PostgreSQL dependency;
- R3 source candidate: host `8083` -> container Tomcat `8080`, same PostgreSQL infrastructure plus analysis history.

The published Compose OCI tag `ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo` is a **last-known-good artifact**. It advances only after publication resolves immutable image digests, pulls the remote Compose artifact again and passes executable verification. A failed publication leaves the prior tag untouched. Repository source state and registry publication state are therefore intentionally not conflated.

The J2 published contract adds R2 to R0/R1 with PostgreSQL. R3 remains independently runnable from source while its candidate PR is under verification; it must not be advertised as a published checkpoint before its own accepted publication proof exists.

Communication semantics:

- browser <-> embedded Tomcat: HTTP through the host-published checkpoint port;
- embedded Tomcat <-> built React assets: same-process static-resource serving;
- React <-> Spring MVC `/api/*`: same-origin HTTP;
- R2/R3 Spring Boot <-> PostgreSQL: JDBC/PostgreSQL protocol on the private Compose network;
- optional Spring Boot <-> live AI provider: HTTPS only when explicitly configured;
- module/port calls: in-process.

No event broker, Redis, separate identity service, WebSocket tier or reverse proxy is introduced without a requirement.

## 12. Concentric delivery activation

![Figure 11 - Concentric delivery rings](diagrams/delivery-rings.svg)

[Authoritative Graphviz/DOT source](diagrams/delivery-rings.dot)

The rings activate capability maturity while preserving the same application core:

- **R0 - deployable hollow shell:** application modules, contracts and replaceable seams; no business-flow acceptance claim.
- **R1 - mandatory synthetic customer review:** customer lookup, CARD/PAYMENT/CRYPTO and source-derived risk evidence on deterministic data.
- **R2 - relational substitution:** Spring JDBC/PostgreSQL/Flyway/Testcontainers behind `CustomerActivityPort`; no invented new operator use case.
- **R3 - mandatory deterministic analysis and reviewable history:** deterministic policy/model adapters, structured analysis, explicit failures, operator attribution and PostgreSQL-backed analysis history.
- **R4 - MUST_HAVE closure:** real policy retrieval/RAG, multi-operator authentication/authorization and related trust boundaries; optional live provider remains behind existing ports.
- **R5 - hardening/demo:** reliability, observability, reviewer polish and NICE_TO_HAVE differentiation without changing established boundaries.

GitHub milestones `J1..J5` are the orthogonal delivery-timebox dimension. A day may activate more than one ring.

### Use-case package to first acceptance ring

| Capability | Delivery priority | First acceptance ring |
| --- | --- | --- |
| Search customer by ID | MANDATORY | R1 |
| Review activity and CARD/PAYMENT/CRYPTO specialization | MANDATORY | R1 |
| Review source-derived risk evidence | MANDATORY | R1 |
| Request structured deterministic analysis | MANDATORY | R3 |
| Persist and inspect deterministic analysis history with operator attribution | R3 delivery core | R3 |
| Retrieve real relevant policy knowledge / RAG | MUST_HAVE | R4 |
| Authenticate/authorize real operators | MUST_HAVE | R4 |

The R3 history activation does not claim R4 security. Authentication and authorization remain a separate acceptance dimension even though the persisted history contract already records an operator identity.

## 13. ADR consistency

The design remains governed by seven accepted ADRs:

1. modular monolith with hexagonal boundaries;
2. provider-neutral analysis plus detection/explanation trust boundary;
3. PostgreSQL + pgvector persistence direction;
4. Java/Spring/React baseline web stack;
5. prebuilt single-image reviewer packaging;
6. Compose OCI multi-platform distribution;
7. Spring JDBC relational adapters.

The R3 refinement does not create a parallel architecture. It activates existing R0 seams, corrects the concrete deterministic adapter name, resolves the previously open analysis-history relational shape and keeps live provider/security/RAG capabilities outside the mandatory R3 boundary.

## 14. Review criterion

A reviewer should be able to answer from this SDD without reconstructing PR history:

- what the system boundary and four application modules are;
- where framework/provider/storage types stop;
- which ports and adapters are stable and which ring activates them;
- why source risk evidence, optional derived detector signals and generated explanation have different authority;
- why external AI transmission is opt-in and absent from default R3 execution;
- how R2 preserves exact PostgreSQL source semantics and snapshot consistency;
- how R3 validates, persists and reloads deterministic analysis history without pretending R4 authentication already exists;
- how `NFR-RES-001` prevents false completed/history state;
- how source and published checkpoint states differ and why `:demo` is last-known-good;
- how R0-R5 extend one architecture concentrically.
