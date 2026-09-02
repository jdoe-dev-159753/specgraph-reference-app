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
3. **Application/domain core:** project-owned contracts and ports define customer/activity, risk, detector, policy, model and history behavior independently of infrastructure.
4. **Infrastructure adapters:** synthetic/Spring-JDBC activity, no-op/future statistical detector, static/pgvector policy, deterministic/live analysis and JDBC history adapters implement those ports.

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
- `analysis`: staged analysis orchestration, detector/policy evidence, model synthesis and analysis history, active from R3.

The current public cross-module direction required by customer review is `customer -> risk`. Analysis may use customer-facing application contracts. Reverse infrastructure dependencies are prohibited.

Spring Modulith verification ratchets the physical graph: the detected module identifiers must remain exactly `identity`, `customer`, `risk`, and `analysis`. Transport, persistence or generic helper packages do not become fifth horizontal modules.

![Figure 2a - UML Package diagram](diagrams/package-modules.svg)

[PlantUML source](diagrams/package-modules.puml)

Hexagonal dependency direction remains strict. Spring MVC, Spring Security, Spring JDBC, PostgreSQL/pgvector, statistical-model libraries and provider SDKs stop at adapters. Application-owned contracts do not import them.

![Figure 2b - Hexagonal architecture](diagrams/hexagonal-architecture.svg)

[PlantUML source](diagrams/hexagonal-architecture.puml)

## 4. Ports, adapters and restrained GoF pattern use

The central outbound ports are:

| Port | Responsibility | Activated behavior |
| --- | --- | --- |
| `CustomerActivityPort` | load one project-owned `CustomerSnapshot` | synthetic R1, Spring JDBC R2+ |
| `RiskSignalDetectorPort` | derive separately identified non-source risk signals from a `CustomerSnapshot` | explicit no-op R4 baseline, optional Bayesian/statistical/graph adapters later |
| `PolicyKnowledgePort` | return project-owned `PolicyEvidence` | static deterministic evidence R3, pgvector R4 |
| `AnalysisModelPort` | consume one project-owned `AnalysisEvidenceEnvelope` and return structured result plus model provenance | deterministic R3/R4 baseline, optional live provider later |
| `AnalysisHistoryPort` | persist/list/inspect validated analysis history | Spring JDBC R3+ |

The primary adapters are `CustomerReviewHttpAdapter`, `AnalysisHttpAdapter`, `SyntheticActivityAdapter`, `JdbcCustomerActivityAdapter`, `NoOpRiskSignalDetectorAdapter`, `StaticPolicyAdapter`, `PgVectorPolicyAdapter`, `DeterministicAnalysisAdapter`, `SpringAiAnalysisAdapter`, and `JdbcAnalysisHistoryAdapter`.

The inception-selected GoF roles remain intentionally limited:

- **Adapter:** translates framework/provider/storage/model APIs to project-owned ports.
- **Strategy:** selects interchangeable activity, detector, policy or analysis-model behavior behind stable ports.
- **Facade/application service:** exposes one coarse-grained use case while hiding multi-port orchestration. `AnalysisService` owns the evidence-to-analysis chain.

Hexagonal architecture is the dependency style containing these roles; it is not itself a Strategy pattern. New patterns are not added to inflate vocabulary.

![Figure 3 - UML Component diagram](diagrams/component-topology.svg)

[PlantUML source](diagrams/component-topology.puml)

## 5. Project-owned contracts

Stable contracts include `CustomerSnapshot`, activity/risk projections, the sealed `AnalysisPipelineArtifact` pivot, `RiskSignalEvidence`, `PolicyEvidence`, `AnalysisEvidenceEnvelope`, `AnalysisResult`, `AnalysisModelProvenance`, `AnalysisModelOutput`, `OperatorId`, `AnalysisHistoryCreateCommand`, and `AnalysisHistoryEntry`.

`AnalysisPipelineArtifact` is the common application-owned pivot for derived analysis-stage artifacts crossing hexagonal adapter boundaries. It is a Java sealed interface whose permitted record variants are exactly `RiskSignalEvidence`, `PolicyEvidence`, and `AnalysisModelProvenance`. The concrete record type is the discriminant. `kind()` and `artifactIdentity()` are derived exhaustively from that concrete type; there is no mutable external tag paired with a generic payload and no design in which two or three irrelevant payload fields must be `null`. Adding a fourth artifact variant must extend the sealed hierarchy and therefore makes exhaustive pattern switches fail compilation until the new variant is handled deliberately.

The pivot standardizes the mechanics that really are common, namely artifact kind, stable artifact identity and provider-neutral metadata, while preserving variant-specific typed payloads. `RiskSignalEvidence` retains detector identity, signal identity and score; `PolicyEvidence` retains retrieved source identity and content; `AnalysisModelProvenance` retains backend and model identities. A common transport/persistence family therefore does not imply common semantic authority. Source `risk_assessments` remain source truth; detector evidence is derived; policy evidence is retrieved context; model/backend provenance describes advisory execution.

`AnalysisEvidenceEnvelope` is the application-owned boundary passed to advisory analysis models. It keeps three semantic layers distinguishable: persisted customer/source-risk facts in `CustomerSnapshot`, optional derived detector signals in `RiskSignalEvidence`, and retrieved policy knowledge in `PolicyEvidence`. Its members remain strongly typed collections rather than a generic `List<AnalysisPipelineArtifact>`, so the compiler also enforces which artifact variants are legal at each stage. Provider- or library-specific context classes do not cross this boundary.

`AnalysisResult` is constrained to a structured risk level `LOW | MEDIUM | HIGH`, a non-empty findings summary and non-empty recommendations. `AnalysisModelOutput` couples that validated application result shape to project-owned backend/model provenance without exposing provider SDK types.

`AnalysisHistoryEntry` adds generated analysis identity, customer identity, operator attribution, generation time, structured result, policy/retrieval provenance, detector provenance and model/backend provenance. These provenance families remain separate typed fields for semantic clarity while their values participate in the same sealed `AnalysisPipelineArtifact` family. They are not flattened into one untyped metadata bag.

Persistence rows, pgvector `Document` values, statistical-library result classes and provider response classes do not become members of these contracts. Adapters must translate them into the corresponding project-owned record variant before the application core sees them.

![Figure 4a - UML Class diagram - project-owned contracts](diagrams/domain-contracts.svg)

[PlantUML source](diagrams/domain-contracts.puml)

![Figure 4b - UML Class diagram - source mapping](diagrams/source-contract-mapping.svg)

[PlantUML source](diagrams/source-contract-mapping.puml)

## 6. Detection versus explanation trust boundary

Source `risk_assessments` are persisted source-shaped evidence. They remain distinct from derived detector evidence and generated analysis.

The R3 deterministic analysis may synthesize customer context, source risk evidence and static policy evidence, but it cannot manufacture a source risk fact. A later live LLM remains advisory explanation/synthesis, not the sole detector or authority for customer risk.

R4 activates the project-owned `RiskSignalDetectorPort` as an explicit stage in the chain. Its default `NoOpRiskSignalDetectorAdapter` deliberately emits no additional signals, so introducing the seam does not change the accepted deterministic R3 risk decision. A later deterministic/statistical/Bayesian/graph/classical-ML implementation can substitute behind the same port when justified by data and benchmark evidence. Its outputs are `RiskSignalEvidence` values with detector identity, signal identity, score and provenance; they never overwrite source `risk_assessments`.

The detector stage and analysis-model stage are therefore intentionally different. A detector may estimate or rank a suspicious pattern from activity evidence; `AnalysisModelPort` receives those derived signals together with source evidence and retrieved policy context and produces bounded advisory synthesis. The OpenAI/Spring AI adapter, when explicitly enabled later, receives the same `AnalysisEvidenceEnvelope` as the deterministic model rather than a provider-specific side channel.

[`ADR-002`](../ADR/ADR-002-provider-neutral-analysis.md) owns this decision and compares candidate detector families.

`CON-AI-002` is a design constraint, not merely a testing convention: **the default configuration does not transmit customer/activity/policy content to an external AI provider**. External transmission requires an explicitly selected live-provider adapter and data permitted for that provider. Merely placing a provider dependency on the classpath does not activate transmission.

## 7. Relational persistence

R2 activates PostgreSQL 17 behind `CustomerActivityPort` using Spring Framework `JdbcClient`. Flyway is the sole schema/migration authority. Explicit SQL maps source relations into project-owned projections; no ORM lifecycle competes with Flyway.

The source relation types include exact monetary `DECIMAL/NUMERIC`, bounded currency/status fields, booleans, country codes and timezone-free `TIMESTAMP`. The adapter verifies the schema contract against the migrated PostgreSQL schema and preserves monetary amounts as exact decimal values independent from currency.

Multi-query customer aggregate reads execute under PostgreSQL `REPEATABLE READ`, so activities and risk evidence cannot be assembled from different committed snapshots.

R3 adds project-owned `analysis_history` through Flyway and `JdbcAnalysisHistoryAdapter`. Only a validated analysis whose persistence succeeds is represented as completed retained history.

The R4 analysis-chain foundation extends each history row with separately serialized detector and model provenance. Existing pre-R4 rows receive an explicit deterministic legacy model identity during migration rather than an unreadable empty object. Policy/retrieval evidence remains in its existing provenance field; detector/model metadata does not mutate source risk tables.

![Figure 5 - Relational persistence model](diagrams/relational-schema.svg)

[PlantUML source](diagrams/relational-schema.puml)

Source `TIMESTAMP` values are wall-clock values without timezone metadata. `specgraph.source-time-zone`, exposed as `SPECGRAPH_SOURCE_TIME_ZONE`, is explicit configuration. The deterministic fixture default is UTC; host JVM/OS timezone is never guessed as source semantics.

## 8. Customer review behavior

`CustomerReviewHttpAdapter -> CustomerReviewUseCase -> CustomerReviewService -> CustomerActivityPort` is the stable read path. R1 uses the synthetic adapter; R2+ substitutes `JdbcCustomerActivityAdapter` without changing the application-owned contract.

Unknown customers return an explicit not-found result rather than fabricated data.

![Figure 6 - UML Activity diagram - customer review](diagrams/activity-customer-review.svg)

[PlantUML source](diagrams/activity-customer-review.puml) | [Sequence view](diagrams/sequence-customer-review.svg)

## 9. Deterministic analysis baseline and R4 staged composition

R3 established the mandatory offline path:

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

The successful R3 path is complete only after history persistence succeeds. Static policy evidence supplies deterministic grounding for R3 without claiming that R4 pgvector/RAG is already implemented.

R4 preserves that behavior while making the complete evidence chain explicit and independently substitutable:

```text
AnalysisHttpAdapter
  -> AnalysisUseCase
  -> AnalysisService
  -> CustomerActivityPort
       -> CustomerSnapshot
          [activities + persisted source risk evidence]
  -> RiskSignalDetectorPort
       -> RiskSignalEvidence[*] implements AnalysisPipelineArtifact
          [default NoOpRiskSignalDetectorAdapter => []]
  -> PolicyKnowledgePort
       -> PolicyEvidence[*] implements AnalysisPipelineArtifact
          [StaticPolicyAdapter until #119 activates pgvector retrieval]
  -> AnalysisEvidenceEnvelope
       [source facts | detector evidence | policy evidence remain distinct and statically typed]
  -> AnalysisModelPort
       -> AnalysisModelOutput
          [validated AnalysisResult + AnalysisModelProvenance implements AnalysisPipelineArtifact]
  -> AnalysisHistoryPort
       [policy/retrieval + detector + model provenance persisted separately]
  -> PostgreSQL
```

This composition means RAG is one context-supply stage, not the complete AI architecture. A Bayesian/statistical detector can later replace only the detector adapter; an OpenAI/live model can later replace only the analysis-model adapter. Both adapters must translate their library/provider-native results into an existing application-owned sealed record variant. Neither substitution changes the application use case or grants generated output authority over source `risk_assessments`.

![Figure 7 - UML Activity diagram - grounded analysis](diagrams/activity-grounded-analysis.svg)

[PlantUML source](diagrams/activity-grounded-analysis.puml) | [Orchestration sequence](diagrams/sequence-analysis.svg) | [Adapter sequence](diagrams/sequence-analysis-adapters.svg)

R3 requires operator **attribution** in persisted provenance but deliberately does not activate R4 authentication/authorization. The R3/R4 deterministic HTTP/application boundary currently supplies a deterministic project-owned `OperatorId` for offline verification. R4 authentication work replaces that deterministic attribution source with real authenticated multi-operator context without changing the history contract.

R3 list/inspect operations therefore prove persistent reviewable history, while authorization of those operations remains a separate R4 acceptance obligation.

![Figure 8 - UML Activity diagram - analysis history review](diagrams/activity-history-review.svg)

[PlantUML source](diagrams/activity-history-review.puml) | [Sequence view](diagrams/sequence-analysis-history.svg)

## 10. Failure and degraded behavior

`NFR-RES-001` maps explicitly to the analysis module and detector/policy/model/history boundaries. These failure modes terminate without a false completed/history state:

- customer not found;
- detector adapter failure;
- insufficient policy grounding;
- policy adapter failure;
- model execution failure;
- structurally invalid model output;
- persistence failure.

Stage ordering is fail-closed. A detector failure stops before policy retrieval/model execution/history persistence. Missing policy evidence means no successfully grounded model execution. Invalid structured output cannot reach history persistence. Failed persistence cannot be reported as retained history.

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
- R3: host `8083` -> container Tomcat `8080`, same PostgreSQL infrastructure plus analysis history.

The published Compose OCI tag `ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo` is a **last-known-good artifact**. It advances only after publication resolves immutable R0/R1/R2/R3/PostgreSQL image digests, binds the complete five-image set into the retained Compose identity, pulls the remote Compose artifact again and passes executable browser verification.

Accepted source checkpoints are preserved through `demo/r0`, `demo/r1`, `demo/r2` and `demo/r3`. A failed publication leaves the previous `:demo` tag untouched. Repository source state and registry publication state are therefore intentionally not conflated.

The complete J2 reviewer contract publishes R0, R1, PostgreSQL-backed R2 and deterministic analysis/history R3 side by side. Until that publication has passed its remote proof, the source candidate remains independently runnable without pretending the registry already contains it.

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
- **R4 - MUST_HAVE closure:** explicit staged detector/retrieval/model/history orchestration, real policy retrieval/RAG, multi-operator authentication/authorization and related trust boundaries; optional live provider remains behind existing ports.
- **R5 - hardening/demo:** reliability, observability, reviewer polish and NICE_TO_HAVE differentiation such as a concrete Bayesian detector or live-provider comparison without changing established boundaries.

GitHub milestones `J1..J5` are the orthogonal delivery-timebox dimension. A day may activate more than one ring.

### Use-case package to first acceptance ring

| Capability | Delivery priority | First acceptance ring |
| --- | --- | --- |
| Search customer by ID | MANDATORY | R1 |
| Review activity and CARD/PAYMENT/CRYPTO specialization | MANDATORY | R1 |
| Review source-derived risk evidence | MANDATORY | R1 |
| Request structured deterministic analysis | MANDATORY | R3 |
| Persist and inspect deterministic analysis history with operator attribution | MUST_HAVE | R3 |
| Compose source, optional detector and policy evidence through one provider-neutral analysis envelope | MUST_HAVE | R4 |
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

The R4 analysis-chain refinement does not create a parallel architecture. It activates the detector seam already anticipated by ADR-002, gives `AnalysisModelPort` one bounded project-owned evidence envelope, introduces the sealed `AnalysisPipelineArtifact` family as the typed pivot shared by detector/retrieval/model-provenance adapters, and extends persisted provenance while preserving the accepted R3 deterministic adapter and source-risk authority. Real pgvector retrieval, authenticated operator context, Bayesian detection and live/OpenAI synthesis remain substitutions or capabilities owned by their separate work nodes rather than being falsely claimed by this foundation.

## 14. Review criterion

A reviewer should be able to answer from this SDD without reconstructing PR history:

- what the system boundary and four application modules are;
- where framework/provider/storage/model-library types stop;
- which ports and adapters are stable and which ring activates them;
- why source risk evidence, optional derived detector signals, retrieved policy evidence and generated explanation have different authority;
- how `AnalysisPipelineArtifact` acts as a sealed tagged-union equivalent whose concrete record type is the compile-time discriminant for detector, retrieval and model/backend provenance artifacts;
- why the pivot shares mechanics without introducing nullable payload branches or erasing stage-specific types;
- how the executable chain composes `CustomerSnapshot -> RiskSignalDetectorPort -> PolicyKnowledgePort -> AnalysisEvidenceEnvelope -> AnalysisModelPort -> AnalysisHistoryPort`;
- why RAG is one grounding/context stage rather than the whole analysis architecture;
- how a future Bayesian detector and optional OpenAI adapter substitute independently behind project-owned ports;
- why external AI transmission is opt-in and absent from default deterministic execution;
- how R2 preserves exact PostgreSQL source semantics and snapshot consistency;
- how deterministic analysis is validated, persisted and reloaded without pretending R4 authentication already exists;
- how `NFR-RES-001` prevents false completed/history state at detector, grounding, model and persistence boundaries;
- how source and last-known-good published checkpoint states differ;
- how the complete J2 publication preserves R0-R3 as independent reviewer checkpoints;
- how R0-R5 extend one architecture concentrically.
