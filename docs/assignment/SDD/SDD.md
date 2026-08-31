# Software Design Description (SDD) — Customer Activity Analytics

**Document ID:** `CAA-SDD-001`  
**Design map:** [`design-map.yaml`](design-map.yaml)  
**Normative requirements:** [`CAA-SRS-001`](../SRS/SRS.md)  
**Machine-readable requirements:** [`requirements.yaml`](../SRS/requirements.yaml)  
**Architecture decisions:** [`ADR-001`](../ADR/ADR-001-modular-monolith-hexagonal.md), [`ADR-002`](../ADR/ADR-002-provider-neutral-analysis.md), [`ADR-003`](../ADR/ADR-003-postgresql-pgvector-persistence.md), [`ADR-004`](../ADR/ADR-004-baseline-web-stack.md), [`ADR-005`](../ADR/ADR-005-prebuilt-demo-container-packaging.md)  
**Verification strategy:** [`CAA-VV-001`](../VV/VV.md)

This document is the canonical human-readable Software Design Description for the reference application. It is intended to be read end-to-end. [`design-map.yaml`](design-map.yaml) remains the machine-readable requirement-to-design mapping. PlantUML and Graphviz/DOT text files in [`diagrams/`](diagrams/) are the maintainable semantic sources for UML and architecture figures; rendered SVG files are generated views embedded below.

## System context and architectural orientation

The system is an operator-facing Customer Activity Analytics application. A customer-care operator uses a browser UI to authenticate, locate a customer, inspect customer activity and risk evidence, request a grounded analysis, and later review persisted analysis history. The application is intentionally delivered as a modular monolith rather than a distributed system: browser/UI, Spring Boot application, PostgreSQL/pgvector persistence, and an optional external AI provider are the runtime boundaries that matter.

At the highest level, the architecture has four concerns before any package-level detail matters:

1. **Operator interaction:** a browser-based React UI exposes customer review and analysis workflows.
2. **Application boundary:** a protected HTTP/JSON API mediates operator-facing capabilities.
3. **Domain/application core:** project-owned contracts and ports define customer/activity, risk, policy grounding, analysis, and history behavior independently of infrastructure choices.
4. **Infrastructure adapters:** synthetic/JPA activity adapters, static/pgvector policy adapters, deterministic/live model adapters, history persistence, and authentication infrastructure implement those ports without becoming the application model.

![Figure 1 — Architectural context schematic](diagrams/system-context.svg)

**Figure 1 — Architectural context schematic — system boundary and external dependencies.** This is a deliberately non-UML overview. It establishes the operator-facing system boundary and the only external runtime dependencies that matter architecturally: PostgreSQL/pgvector and the optional live AI provider. The mandatory baseline does not require that provider; internal modular-monolith calls are in-process.

[PlantUML source](diagrams/system-context.puml)

The remaining figures progressively zoom from this system-level view into package structure, hexagonal ports/adapters, components, contracts, persistence, behavior, deployment, and delivery activation.

## Figure notation and UML 2.5.1 profile

The SDD uses standard UML 2.5.1 notation where that notation fits the engineering question, and explicitly labels non-UML architecture schematics instead of presenting arbitrary rendering-tool shapes as a new diagram type.

- **Figure 1:** architectural context schematic, non-UML.
- **Figure 2a:** UML Package diagram.
- **Figure 2b:** hexagonal-architecture ports/adapters schematic, non-UML.
- **Figure 3:** UML Component diagram.
- **Figures 4a/4b:** UML Class diagrams.
- **Figure 5:** entity-relationship view of the relational schema, non-UML.
- **Figures 6–9:** UML Activity diagrams using ActivityPartitions (swimlanes) to preserve ordering and responsibility without oversized lifelines.
- **Figure 10:** UML Deployment diagram.
- **Figure 11:** concentric delivery-ring schematic, non-UML, authored as Graphviz/DOT text and rendered to SVG.

Detailed Sequence diagrams remain available as supplementary design sources where message-level interaction ordering is useful, but they are not the primary embedded workflow views when their lifeline geometry makes normal GitHub reading worse.

## Authority model

- [`CAA-SRS-001`](../SRS/SRS.md) owns normative requirements, invariants, assumptions and acceptance criteria.
- [`requirements.yaml`](../SRS/requirements.yaml) owns machine-readable requirement identity, provenance and acceptance links.
- [`ADR-001`](../ADR/ADR-001-modular-monolith-hexagonal.md), [`ADR-002`](../ADR/ADR-002-provider-neutral-analysis.md), [`ADR-003`](../ADR/ADR-003-postgresql-pgvector-persistence.md), [`ADR-004`](../ADR/ADR-004-baseline-web-stack.md), and [`ADR-005`](../ADR/ADR-005-prebuilt-demo-container-packaging.md) own independently reviewable architecture decisions.
- [`design-map.yaml`](design-map.yaml) owns the current mapping from requirements to design elements, ports, adapters, ADRs and delivery rings.
- PlantUML and Graphviz/DOT sources in [`diagrams/`](diagrams/) own diagram semantics; rendered SVG files are generated views.
- implementation and executable verification become authoritative for their concrete behavior once introduced, but do not silently rewrite requirements or ADR rationale.

If code diverges from this map, the divergence must be reconciled by changing the design artifact/ADR or the code. A stale SDD is not permitted to remain apparently authoritative merely because Markdown is patient and never complains.

## Application module architecture

The backend implementation is one Spring Boot modular monolith with four application modules: `identity`, `customer`, `risk`, and `analysis`. The package view below is explicitly a UML Package diagram. It uses fully qualified package labels such as `analysis.application` and `analysis.adapter.out.model` rather than context-dependent abbreviations such as `application` or `out/ai` whose meaning disappears as soon as the enclosing box is cropped from the page.

Hexagonal dependency direction is strict: adapter packages depend inward on project-owned domain/application contracts. Spring MVC, Spring Security, JPA/Hibernate, PostgreSQL/pgvector and Spring AI remain outside the application core.

![Figure 2a — UML Package diagram — application module architecture](diagrams/package-modules.svg)

**Figure 2a — UML Package diagram — application module architecture.** Packages are UML namespaces; dashed arrows are dependencies. The expanded labels make package identity explicit while preserving the inward dependency rule.

[PlantUML source](diagrams/package-modules.puml)

The same structure is easier to understand architecturally when drawn in the notation that motivated the decision in [`ADR-001`](../ADR/ADR-001-modular-monolith-hexagonal.md): an actual hexagon with driving adapters on one side, driven adapters on the other, and named ports between infrastructure and application policy.

![Figure 2b — Hexagonal architecture — ports and adapters](diagrams/hexagonal-architecture.svg)

**Figure 2b — Hexagonal architecture — ports and adapters.** Non-UML architectural schematic. The hexagon is the project-owned application/domain core; `CustomerActivityPort`, `PolicyKnowledgePort`, `AnalysisModelPort`, and `AnalysisHistoryPort` are explicit outbound seams. React/HTTP/Security drive use cases from the left; persistence, policy, model and history adapters are replaceable infrastructure on the right.

[PlantUML source](diagrams/hexagonal-architecture.puml)

### Gang of Four pattern roles inside the hexagonal design

The inception document deliberately selected three GoF patterns and warned against pattern bingo: **Adapter**, **Strategy**, and **Facade/application service**. The SDD makes all three explicit. Hexagonal architecture itself is not a Strategy pattern; it is the architectural style in which these smaller pattern roles operate.

| Pattern | Role in this design | Concrete examples |
| --- | --- | --- |
| **Adapter** | Translate infrastructure/framework/provider APIs into application-owned ports and contracts | `SyntheticActivityAdapter`, `JpaCustomerActivityAdapter`, `StaticPolicyAdapter`, `PgVectorPolicyAdapter`, `DeterministicAnalysisStub`, `SpringAiAnalysisAdapter`, `JpaAnalysisHistoryAdapter` |
| **Strategy** | Select interchangeable behavior behind one stable port without provider/storage branching in the application core | synthetic vs JPA activity, static vs pgvector policy retrieval, deterministic vs live model implementation |
| **Facade / application service** | Expose a coarse-grained use case while hiding orchestration across multiple ports | analysis application service coordinating customer context, policy retrieval, model execution, result validation and history persistence |

This mapping follows the reuse/pattern blueprint in [`Inception.md`](../Inception/Inception.md#12-reuse-first-and-restrained-patterns) and is reflected in [`ADR-001`](../ADR/ADR-001-modular-monolith-hexagonal.md) and [`ADR-002`](../ADR/ADR-002-provider-neutral-analysis.md). Composition, constructor injection, records/final classes and framework-supplied Repository abstractions remain implementation techniques; they are not promoted to additional GoF patterns merely to inflate the vocabulary.

## Components and interfaces

The component view makes provided/required seams explicit. The web client requires the protected HTTP/JSON surface. Inside the backend, application modules require project-owned outbound ports (`CustomerActivityPort`, `AnalysisModelPort`, `PolicyKnowledgePort`, `AnalysisHistoryPort`); replaceable adapters provide those ports. Operator-facing HTTP code depends on coarse-grained application use cases/facades rather than coordinating persistence, retrieval and model adapters itself.

![Figure 3 — UML Component diagram — application components and interfaces](diagrams/component-topology.svg)

**Figure 3 — UML Component diagram — application components and interfaces.** Components and interfaces show structural dependencies across the React client, protected HTTP boundary, application modules, and replaceable outbound adapters. Transport is labelled only when the relation crosses a real runtime boundary; the project-owned ports are in-process interfaces.

[PlantUML source](diagrams/component-topology.puml)

## Application contracts and source mapping

The stable application contracts are `CustomerSnapshot`, project-owned activity/risk projections, `AnalysisResult`, `PolicyEvidence`, `OperatorId`, `AnalysisHistoryCreateCommand`, and `AnalysisHistoryEntry`. The first Class diagram deliberately contains only project-owned classifiers.

![Figure 4a — UML Class diagram — project-owned application contracts](diagrams/domain-contracts.svg)

**Figure 4a — UML Class diagram — project-owned application contracts.** Stable customer-review, analysis and history contracts. Persistence relations, JPA entities and provider response types are deliberately absent from these contracts.

[PlantUML source](diagrams/domain-contracts.puml)

The second Class diagram answers a different question: how source persistence concepts become the smaller project-owned projections consumed by the application core.

![Figure 4b — UML Class diagram — source-to-application mapping](diagrams/source-contract-mapping.svg)

**Figure 4b — UML Class diagram — source-to-application mapping.** Adapter mapping from source transaction/risk concepts into `ActivityProjection` and `RiskEvidence`. Source/JPA relation classes stop at the adapter boundary and never become durable members of `CustomerSnapshot`.

[PlantUML source](diagrams/source-contract-mapping.puml)

## Relational persistence model

The relational view is an entity-relationship view, not a UML Class diagram. It reproduces the source relation names, keys and data types supplied by `SRC-001`, including `DECIMAL(18,2)`, `VARCHAR(10)`, `VARCHAR(4)`, `BOOLEAN`, `CHAR(2)`, `TIMESTAMP`, `TEXT`, and `DECIMAL(5,2)`. R2 activates PostgreSQL behind `CustomerActivityPort`; R3 proves the mandatory deterministic analysis contract; R4 activates project-owned analysis history, authentication, and pgvector policy retrieval. Source details that are not supplied remain explicitly unspecified rather than being filled with plausible-looking fiction.

![Figure 5 — Entity-relationship view — relational persistence model](diagrams/relational-schema.svg)

**Figure 5 — Entity-relationship view — relational persistence model.** `transactions`, CARD/PAYMENT/CRYPTO specialization tables, `risk_assessments`, and `risk_rules` carry the exact source-level keys and SQL types from the assignment. `customers` and `analysis_history` are clearly separated project-owned extensions. Alice Example, Bob Example, and John Doe appear only as illustrative synthetic fixture labels; they are not source schema fields or normative customer facts.

[PlantUML source](diagrams/relational-schema.puml)

The selected relational read path is singular: `CustomerActivityPort` is implemented by `JpaCustomerActivityAdapter`, which maps source customer/activity/risk relations into project-owned `CustomerSnapshot`, `ActivityProjection`, and `RiskEvidence` contracts. The `risk` application module does not introduce a competing persistence adapter for the same source risk evidence.

## Customer review behavior

For workflow-level behavior the SDD uses UML Activity diagrams with ActivityPartitions, commonly rendered as swimlanes. This keeps sequential control flow and responsibility explicit while avoiding the large horizontal whitespace produced by long Sequence-diagram lifelines.

![Figure 6 — UML Activity diagram — authenticated customer review](diagrams/activity-customer-review.svg)

**Figure 6 — UML Activity diagram — authenticated customer review.** Swimlanes separate operator, React UI, security/HTTP boundary, customer/risk application and activity adapter responsibilities. Authentication rejection, synthetic-versus-relational substitution, unknown-customer handling and successful activity/risk rendering remain ordered and explicit.

[PlantUML source](diagrams/activity-customer-review.puml) · [Supplementary Sequence diagram](diagrams/sequence-customer-review.svg)

## Grounded analysis behavior

Analysis first loads customer context through `CustomerActivityPort`, obtains policy evidence behind `PolicyKnowledgePort`, runs a deterministic or configured live model behind `AnalysisModelPort`, validates the structured result, and persists through `AnalysisHistoryPort`.

No relevant policy evidence terminates the successfully grounded flow with an explicit insufficient-grounding result. Model/provider failure, invalid structured output, and persistence failure likewise terminate explicitly and cannot fall through into completed/retained history.

![Figure 7 — UML Activity diagram — grounded analysis workflow](diagrams/activity-grounded-analysis.svg)

**Figure 7 — UML Activity diagram — grounded analysis workflow.** ActivityPartitions show the responsibility handoff from operator/UI through the application and policy/model/history adapters. Decision nodes preserve the exact successful and failure paths without requiring a fifteen-lifeline embedded image.

[PlantUML source](diagrams/activity-grounded-analysis.puml) · [Supplementary orchestration Sequence diagram](diagrams/sequence-analysis.svg) · [Supplementary adapter Sequence diagram](diagrams/sequence-analysis-adapters.svg)

## Analysis history review

Authenticated operators can list and inspect prior analyses through `AnalysisHistoryPort`. The read contract is `AnalysisHistoryEntry`, which carries analysis/customer identity, generating operator, generation time, structured result and evidence provenance required by `AC-HIST-002`. `AnalysisHistoryCreateCommand` is the separate write input and intentionally lacks the generated analysis identity.

![Figure 8 — UML Activity diagram — analysis history review](diagrams/activity-history-review.svg)

**Figure 8 — UML Activity diagram — analysis history review.** The partitions preserve authentication, application-port, persistence-adapter and UI responsibilities while showing the read contract returned to the operator.

[PlantUML source](diagrams/activity-history-review.puml) · [Supplementary Sequence diagram](diagrams/sequence-analysis-history.svg)

## Failure and degraded behavior

The negative paths are consolidated into a single UML Activity diagram because the engineering question is control-flow validity, not message timing. Unauthenticated access, missing grounding, model execution failure, invalid structured output and persistence failure all terminate explicitly. None may be represented as a successfully completed or retained analysis.

![Figure 9 — UML Activity diagram — failure and degraded behavior](diagrams/activity-failure-behavior.svg)

**Figure 9 — UML Activity diagram — failure and degraded behavior.** Decision nodes and swimlanes make each terminating failure path visible on one readable canvas. In particular, no-evidence cannot fall through to model execution, invalid output cannot reach persistence, and failed persistence cannot be described as retained history.

[PlantUML source](diagrams/activity-failure-behavior.puml)

For reviewers who want message-level detail, the narrower supplementary Sequence views remain available: [grounding/authentication](diagrams/sequence-failure-grounding.svg), [model/validation](diagrams/sequence-failure-model.svg), [persistence](diagrams/sequence-failure-persistence.svg), and the [comprehensive semantic source](diagrams/sequence-failure-modes.puml).

## Deployment and communication topology

The application remains a modular monolith at runtime. For R0/R1 reviewer packaging, React and the Spring MVC API are not independent deployed services: React is compiled before runtime, copied into Spring Boot classpath static resources, and packaged into the same executable JAR as the Java application. Embedded Tomcat is therefore the single HTTP execution environment for both browser assets and `/api/*` endpoints.

![Figure 10 — UML Deployment diagram — packaged runtime and optional external nodes](diagrams/deployment-topology.svg)

**Figure 10 — UML Deployment diagram — packaged runtime and optional external nodes.** The `Docker Compose / Linux host` contains one R0/R1 application container. Its Java 21 runtime executes one Spring Boot JAR; embedded Tomcat serves the built React assets at `/` and Spring MVC endpoints at `/api/*` on the same host-published port. PostgreSQL/pgvector remains a later-ring Compose service when R2+ persistence is activated, and the external AI provider remains optional behind the provider-neutral adapter seam. No frontend-to-API network hop exists inside the R0/R1 package.

[PlantUML source](diagrams/deployment-topology.puml)

Figure 10 is the deployment shape of **one checkpoint instance**. The J1 reviewer comparison instantiates that same shape twice on one Docker host: the R0 image maps container Tomcat `:8080` to host `:8080`, while the R1 image maps container Tomcat `:8080` to host `:8081`. The two instances are complete product checkpoints, not REST API contract versions; using separate host ports deliberately avoids introducing a reverse proxy or `/r0`/`/r1` context-path layer solely for presentation. This side-by-side topology lets a reviewer keep the hollow R0 shell and the first MANDATORY R1 slice open simultaneously while preserving identical package/runtime boundaries.

Local execution remains the mandatory baseline defined by [`ASM-DEP-001`](../SRS/SRS.md#asm-dep-001--local-execution-is-the-mandatory-deployment-baseline). The same single-image Compose topology may run on a dedicated Docker-capable reviewer VM or VPS-style host. The externally reachable host is not an application dependency and is supplied through host-local `DEMO_HOST`, `R0_PORT`, `R1_PORT` and optional full-URL overrides rather than hard-coded into source or design. This preserves `AMB-DEP-001`: the assignment still does not prescribe a production deployment target.

The human demo entry point is part of `INF-COMPOSE-001`: `./scripts/demo-up.sh` pulls both prebuilt checkpoint images, starts R0 and R1 detached with `--no-build`, waits for both healthchecks, then prints `R0 ready: <URL>` and `R1 ready: <URL>`. The default host mappings are R0 `:8080` and R1 `:8081`; a reviewer host may configure browser-reachable addressing once in the untracked `.env.demo`. Individual `r0`/`r1` script modes remain diagnostic conveniences rather than the nominal presentation path.

This packaging decision is controlled by [`ADR-005`](../ADR/ADR-005-prebuilt-demo-container-packaging.md). Node/Vite and Maven are build-stage tools, not persistent reviewer runtimes. A separate Caddy/nginx frontend service or reverse proxy was explicitly rejected for J1 because React does not need an independent runtime process here. Embedded Tomcat already owns the required HTTP serving boundary.

TLS termination, ingress routing, DNS, load balancing or router forwarding remain deployment-profile concerns. They can be introduced by a future concrete public-deployment decision but are not hidden prerequisites for local execution, CI verification or the dedicated-VM demo profile.

Communication semantics are explicit where known:

- browser ↔ embedded Tomcat: HTTP through the host-published checkpoint port (R0 `8080`, R1 `8081` by default; container port `8080` in both cases);
- embedded Tomcat ↔ built React assets: same-process static-resource serving from the executable JAR, with no network transport;
- React browser code ↔ Spring MVC `/api/*`: same-origin HTTP to the same Tomcat process;
- Spring Boot ↔ PostgreSQL/pgvector: JDBC/PostgreSQL protocol over private TCP when the relational adapter is activated in R2+;
- Spring Boot ↔ optional external AI provider: HTTPS provider API when explicitly configured;
- module/port interactions within the modular monolith: in-process calls.

No WebSocket, event broker, FIFO, Redis, separate identity service or other transport/process is introduced merely to make the architecture look more distributed.

## Concentric delivery activation

The design is activated through concentric rings rather than parallel throwaway architectures. The visual matters here because the key design claim is geometric: every outer ring encloses and extends the same architecture.

![Figure 11 — Concentric delivery rings](diagrams/delivery-rings.svg)

**Figure 11 — Concentric delivery rings.** Onion-style activation model from the deployable R0 shell through R1 mandatory synthetic customer review, R2 relational substitution, R3 mandatory deterministic analysis, R4 MUST_HAVE closure, and R5 hardening/demo. Structural seams may exist before the requirement they support first becomes an acceptance obligation; each outer ring still extends the same architecture rather than creating a second one.

[Authoritative Graphviz/DOT source](diagrams/delivery-rings.dot)

The SVG is generated from the text source; it is not maintained by hand. Graphviz is used here for the same reason as in the inception concentric-ring figure: exact concentric geometry is the semantic point of the view. Mermaid remains appropriate for diagram types it models directly, but its current diagram catalogue has no native onion/concentric-ring type, so forcing this view through a generic flowchart layout would make the source simpler only by making the geometry less authoritative.

The ring semantics are:

- **R0 — deployable hollow shell:** Java/Spring/React deployment shell, application modules, project-owned contracts and replaceable ports/adapters. R0 deliberately carries no business-use-case acceptance obligation;
- **R1 — mandatory synthetic customer review:** first acceptance of customer search, CARD/PAYMENT/CRYPTO activity review and source-derived risk evidence on deterministic synthetic data. Authentication is not an R1 gate;
- **R2 — relational substitution:** replace the synthetic activity implementation with JPA/PostgreSQL/Flyway/Testcontainers behind `CustomerActivityPort`; no new operator use case is invented merely because the adapter becomes realistic;
- **R3 — mandatory deterministic analysis:** first acceptance of requesting analysis and producing the mandatory structured risk level/findings/recommendations through the existing provider-neutral model port;
- **R4 — MUST_HAVE closure:** activate multi-operator authentication/security, relevant-policy retrieval/RAG, completed-analysis persistence and history review. Optional live provider integration remains behind the existing ports;
- **R5 — hardening/demo:** reliability, observability, NICE_TO_HAVE differentiation and demo polish without changing the established boundaries.

`first_acceptance_ring` is a delivery projection, not a UML relationship. The completed-system use-case diagrams may therefore show a MUST_HAVE behavior as an `<<include>>` of a MANDATORY use case even though the mandatory requirement receives its first deterministic acceptance evidence in an inner ring. Structural elements such as `OperatorId`, ports, or adapter seams may likewise exist before their user-visible capability is activated.

### Use-case package to delivery-ring allocation

The SRS use-case Packages are the logical grouping authority for coherent operator capabilities. Requirements remain traceability metadata; they are not drawn as Requirement elements inside the UML use-case diagrams. This table projects those packaged capabilities onto the concentric delivery plan without changing UML ownership or `<<include>>`/`<<extend>>` semantics.

| UML Package | Requirement-backed use-case capability | Delivery priority | First acceptance ring |
| --- | --- | --- | --- |
| Identity and customer selection | Search Customer by ID | MANDATORY | R1 |
| Identity and customer selection | Authenticate Operator | MUST_HAVE | R4 |
| Customer activity review | Review dashboard/activity and CARD/PAYMENT/CRYPTO specializations | MANDATORY | R1 |
| Risk evidence review | Review source-derived risk evidence | MANDATORY | R1 |
| AI-assisted customer analysis | Request AI analysis / produce structured risk analysis | MANDATORY | R3 |
| AI-assisted customer analysis | Retrieve relevant policy knowledge | MUST_HAVE | R4 |
| AI-assisted customer analysis | Persist completed analysis | MUST_HAVE | R4 |
| Analysis history review | Review previous analyses | MUST_HAVE | R4 |

A Package may span rings because UML packaging answers **which use cases belong together**, while the concentric projection answers **when each requirement-backed capability first becomes acceptable**. R0 and R2 intentionally have no new user-visible use-case acceptance: they establish structure and substitute infrastructure respectively.

A later ring substitutes infrastructure behind stable seams. It does not introduce a second architecture merely because a more realistic adapter is available.

## ADR consistency

The current design remains consistent with the five accepted architecture decisions:

- [`ADR-001 — Modular monolith with hexagonal boundaries`](../ADR/ADR-001-modular-monolith-hexagonal.md);
- [`ADR-002 — Provider-neutral analysis`](../ADR/ADR-002-provider-neutral-analysis.md);
- [`ADR-003 — PostgreSQL + pgvector persistence`](../ADR/ADR-003-postgresql-pgvector-persistence.md);
- [`ADR-004 — Baseline Java/Spring/React web stack`](../ADR/ADR-004-baseline-web-stack.md);
- [`ADR-005 — Prebuilt single-image reviewer packaging`](../ADR/ADR-005-prebuilt-demo-container-packaging.md).

The contract refinements made during design review, including the split between `AnalysisHistoryCreateCommand` and `AnalysisHistoryEntry`, refine the application design without invalidating those decisions. The explicit Adapter/Strategy/Facade mapping restores inception intent inside the accepted architectural decisions, while ADR-005 separately records the concrete packaging boundary because moving from development-process containers to one executable-JAR runtime is a durable deployment decision rather than mere terminology.

## Review criterion

A reviewer should be able to answer from this document, without reconstructing PR diffs:

- what system and operator context the architecture serves;
- which figures are UML 2.5.1 diagrams and which are explicitly non-UML architecture schematics;
- how the modular-monolith packages depend on each other;
- how the hexagonal core, ports, driving adapters and driven adapters relate;
- where Adapter, Strategy and Facade/application-service roles actually occur and why they are not themselves the architectural style;
- which contracts and ports are application-owned;
- how the supplied relational schema and exact source types map into those contracts;
- how the principal successful and failure workflows execute;
- where each network or persistence protocol actually occurs;
- how one prebuilt Spring Boot image shape supports two simultaneously runnable reviewer checkpoints (R0 and R1) without adding a frontend runtime or routing tier;
- how the R0–R5 concentric delivery rings extend one architecture;
- which accepted ADR explains each major design choice.