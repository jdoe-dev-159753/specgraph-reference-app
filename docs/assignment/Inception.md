# Swissquote Customer Activity Analytics — Inception and Concentric Delivery Plan

**Document role:** challenge-specific inception reference  
**Authority:** reference rationale; the SRS, SDD, ADRs, tests, code, and GitHub metadata remain authoritative for their own semantics  
**Timebox:** five implementation days  
**Source:** the supplied Customer Activity Analytics take-home statement and database schema, retained outside the durable generic repository layer

## 1. Purpose

This document records the engineering reasoning used to turn the take-home statement into a five-day, specification-driven delivery strategy before detailed requirements, design, verification artefacts, and implementation work are instantiated.

The goal is not to build an impressive framework around a small exercise. The goal is to make the requested system demonstrable early, keep architectural decisions reviewable, reduce integration risk continuously, and preserve enough engineering evidence to explain how AI-assisted implementation was controlled.

The central delivery rule is:

> Build a complete but hollow application first, deploy it, then replace stub adapters concentrically until the same shell becomes the final application.

This avoids both waterfall sequencing and disposable-prototype rewrites.

## 2. Assignment facts that shape the plan

The supplied statement requires a web dashboard for customer-care operators that can:

- search a customer by Customer ID;
- review CARD, PAYMENT, and CRYPTO activity stored in a relational database;
- provide a clear activity/risk overview;
- request an AI analysis producing a risk level, findings summary, and recommendations;
- authenticate multiple operators;
- use RAG to retrieve relevant unstructured knowledge and policies;
- persist AI analyses for later review.

The preferred stack is Java 17+, Spring Boot, Hibernate/JPA, React, and a relational database. Supporting technologies and the LLM provider are open choices, and the statement explicitly permits stubs for actual LLM calls.

The supplied schema already contains transactions, the three specialized activity tables, `risk_assessments`, and `risk_rules`. The existence of triggered assessments means the exercise does not require inventing a generic rules engine merely because `threshold_logic` exists in the schema.

Delivery requires a Git repository, a README explaining execution, architecture, main decisions, and assumptions, plus a 10–15 minute demo. It also asks for a short description of LLM choices and agent instructions.

## 3. Planning model: source obligation != delivery priority

Two independent dimensions must not be conflated:

1. **Requirement origin** — explicitly requested by the assignment or derived by engineering.
2. **Delivery priority** — how early a capability must become real in the concentric implementation.

Delivery priority uses three values:

- **MANDATORY** — belongs to the first useful centre of the application; failure prevents a meaningful core demo.
- **MUST_HAVE** — required for the final credible submission but may be layered after the centre already works.
- **NICE_TO_HAVE** — useful differentiation that may be dropped without compromising the requested product.

A personal GitHub Project v2 may use one single-select field named `Delivery priority` with exactly those values. It is planning metadata, not issue lifecycle. Issue lifecycle, hierarchy, dependencies, duplicates, ownership, and PR relationships remain GitHub-native.

## 4. Functional perimeter

| Capability | Delivery priority | Source |
| --- | --- | --- |
| Search customer by Customer ID | MANDATORY | Assignment |
| Review CARD/PAYMENT/CRYPTO activity | MANDATORY | Assignment |
| Review risk signals / aggregate risk view | MANDATORY | Assignment goal + supplied risk schema |
| Request structured AI analysis | MANDATORY | Assignment; deterministic stub initially allowed |
| Authenticate different operators | MUST_HAVE | Assignment |
| Retrieve relevant policies with RAG | MUST_HAVE | Assignment |
| Persist AI analysis | MUST_HAVE | Assignment |
| Review previous analyses | MUST_HAVE | Assignment |
| Filter activity by type/date/status | NICE_TO_HAVE | Derived usability |
| Inspect enriched transaction detail | NICE_TO_HAVE | Derived usability |
| Use a live external LLM provider | NICE_TO_HAVE | Derived; assignment permits LLM stubs |
| Automatic refresh/monitoring | NICE_TO_HAVE | Derived interpretation of dashboard monitoring |

The exact requirement catalogue and acceptance criteria belong in the SRS, not here.

## 5. 2TUP-style functional and non-functional views

The SRS must contain two separate PlantUML use-case views:

1. a **functional use-case diagram**;
2. a **non-functional 2TUP use-case diagram**.

Use-case bubbles carry delivery priority visually and textually so the view remains readable without colour:

- light red + `<<MANDATORY>>`;
- amber + `<<MUST_HAVE>>`;
- light green/blue + `<<NICE_TO_HAVE>>`.

Use UML `<<include>>` and `<<extend>>` only where their semantics are real. Authentication should normally be a protected-use-case precondition rather than a decorative `include` repeated from every bubble.

Expected functional relationships include:

- review customer activity includes CARD, PAYMENT, and CRYPTO views;
- request analysis includes context construction and structured risk/findings/recommendations production;
- context construction includes policy retrieval once RAG is active;
- final analysis includes persistence;
- filtering and transaction drill-down extend activity review;
- history review extends the customer dashboard workflow.

Candidate non-functional use cases include reproducible execution, deterministic verification, external-LLM independence, secure operator access, hexagonal boundary preservation, persistent demo data, equivalent local/remote deployment, graceful AI-provider failure, and health/readiness checks. Generated technical documentation and advanced observability remain optional unless later requirements promote them.

## 6. Documents are illustrated engineering artefacts

UML is not a detached gallery. Each diagram belongs semantically to the controlled document it explains.

### SRS minimum views

- functional 2TUP use-case diagram;
- non-functional 2TUP use-case diagram;
- concise system context where useful for scope.

### SDD minimum views

- system/context view;
- module/component diagram;
- hexagonal architecture view;
- domain/class view where it clarifies stable concepts;
- database/schema view;
- customer-query sequence;
- AI-analysis/RAG sequence;
- deployment diagram.

PlantUML source is authoritative for UML views. Rendered SVG/HTML/PDF is generated output. Spring Modulith-generated module diagrams should complement, not duplicate manually maintained implementation structure.

## 7. Target technical architecture

Use a **modular monolith**, not microservices.

Recommended core stack:

- Java 21 within the assignment's Java 17+ constraint;
- Spring Boot;
- Spring Modulith;
- Spring MVC;
- Spring Data JPA / Hibernate;
- Flyway;
- Spring Security;
- PostgreSQL + pgvector;
- Spring AI for provider integration, structured output, RAG, and PgVectorStore;
- React + TypeScript + MUI;
- TanStack Query;
- Testcontainers;
- springdoc-openapi;
- PlantUML;
- Docker Compose + Caddy.

Likely business modules:

- `identity`;
- `customer`;
- `risk`;
- `analysis`.

RAG is a capability behind an `analysis` output port, not a reason to manufacture another bounded context.

Within a module, preserve strict hexagonal dependency direction:

```text
domain
application
  port/in
  port/out
adapter
  in/web
  out/persistence
  out/ai
  out/knowledge
```

Prefer composition to inheritance: constructor injection, final classes/records where appropriate, interfaces for ports and genuine polymorphism, and no speculative `AbstractFooService -> FooServiceImpl` hierarchies. Framework-native persistence or provider types must not become durable domain/application contracts.

## 8. Reuse-first and restrained patterns

Minimize project-owned infrastructure code.

Expected mature capabilities include:

- Spring Modulith + jMolecules/ArchUnit for architectural verification;
- Datafaker for reproducible generic fixture fields;
- Testcontainers for PostgreSQL/pgvector integration evidence;
- Spring AI `ChatClient`/structured output rather than custom LLM protocol/parsing machinery;
- Spring AI RAG/PgVectorStore rather than a hand-built retrieval framework;
- MUI/TanStack Query rather than custom frontend infrastructure;
- springdoc-openapi rather than handwritten REST documentation.

Adopt extra dependencies only when they remove real project-owned complexity. For example, MapStruct is useful if mapping volume justifies it; it is not a badge to collect on day one.

Use GoF patterns where the design naturally requires them:

- **Adapter** for infrastructure implementations behind ports;
- **Strategy** for interchangeable stub/live providers;
- **Facade/application service** for analysis orchestration.

Do not turn the exercise into pattern bingo.

## 9. Stubs are production-boundary substitutes

A stub is an implementation of the same stable port that a later real adapter will implement. It is not a throwaway parallel architecture.

Examples:

```text
AnalysisModelPort
  -> DeterministicAnalysisStub
  -> SpringAiAnalysisAdapter

PolicyKnowledgePort
  -> StaticPolicyAdapter
  -> PgVectorPolicyAdapter

CustomerActivityPort
  -> SyntheticActivityAdapter
  -> JpaCustomerActivityAdapter
```

The deterministic AI stub should consume real activity/risk context and return the final structured result shape, for example:

```text
riskLevel
summary
findings[]
recommendations[]
```

Keep the stub available in the final repository as an offline/demo fallback. The assignment explicitly allows it, and a recruiter demo should not become hostage to an external quota or credential.

## 10. Synthetic data strategy

Create approximately 5–8 deterministic demo customers with coherent temporal stories rather than independent uniform random rows.

Representative profiles:

- stable low risk;
- progressively growing cross-border activity;
- sudden cryptocurrency burst;
- card failures/reversals burst;
- high-value wire transfer;
- mixed anomalous behaviour.

Use Datafaker for generic identities, accounts, merchants, and descriptors. Keep the project-specific temporal generator small: baseline distributions plus a lightweight random walk or mean-reverting evolution and explicit injected shocks.

Use a fixed seed so the same repository revision and seed produce the same demo customers, graphs, risk evidence, and tests. Seed `risk_assessments` coherently with the scenarios instead of building an unnecessary generic rules engine.

## 11. Concentric delivery rings

### R0 — Hollow mock-up and architecture proof

Build the real shell immediately:

```text
Browser
  -> Caddy / HTTPS
  -> React
  -> Spring Boot
  -> application ports
  -> stub adapters
  -> PostgreSQL/pgvector container present but minimally used
```

R0 already has real frontend/backend routing, DTOs, ports, module boundaries, Compose topology, health endpoints, and deployment. Fake customer activity, risk, and analysis make the complete user journey visible.

Deploy R0 to the VPS on day 1. Expose the deployed Git SHA through application info or the UI. If application authentication is not yet ready, temporary infrastructure-level demo protection may be used and then replaced.

R0 is the application, not a disposable prototype.

### R1 — Real read path through synthetic domain data

Replace the customer/activity stub with the deterministic synthetic adapter. Customer ID search and CARD/PAYMENT/CRYPTO activity become behaviourally meaningful. Add unit/contract/UI tests, minimal OpenAPI, and module-boundary checks.

### R2 — Real persistence and operator identity

Introduce Flyway, JPA/Hibernate, PostgreSQL persistence, deterministic database seeding, and Testcontainers. Replace synthetic storage behind the same port. Implement Spring Security/operator persistence on its own independent path where possible. Add schema validation, health/readiness, secret handling, and deterministic demo reset.

### R3 — Risk + end-to-end AI analysis with deterministic model stub

Use supplied `risk_assessments` as risk evidence, aggregate it into the dashboard, and connect the real analysis orchestration to `DeterministicAnalysisStub`. Persist results and expose history. At this point the core demo works end to end without a network LLM.

### R4 — Real RAG independently of the LLM provider

Replace static policy lookup with document ingestion, embeddings, pgvector storage, and actual retrieval while keeping the deterministic AI adapter. Prove retrieval correctness independently from provider behaviour.

Then, if healthy, replace only `AnalysisModelPort` with the Spring AI external-provider adapter.

### R5 — Live provider hardening and optional polish

Exercise provider failures, structured-output validation, timeouts, fallback behaviour, and remaining UX improvements. Only after all required delivery obligations are green may optional filters, richer charts, citations, broader E2E coverage, or automated CD consume time.

## 12. Deployment is an early continuous test

Maintain two supported paths:

1. local fallback: `docker compose up --build`;
2. live demo: HTTPS on one small Linux VPS.

Target topology:

```text
DNS
 -> Caddy
    -> React static content
    -> /api -> Spring Boot
               -> PostgreSQL + pgvector
```

Do not expose the database port publicly. Keep provider secrets outside Git. Provide deterministic demo-data reset. Prefer a simple known-good deployment script taking an exact Git SHA before automating the same procedure through CI/CD.

After each meaningful ring:

```text
reviewable head -> deterministic checks -> deploy -> external smoke test
```

The first reference-app GitHub Actions bootstrap attempt failed before any job step executed. Runner availability must therefore be proven in R0 rather than assumed. If self-hosted CI is required, provisioning it is part of the early technical architecture proof.

## 13. GitHub issues and pull requests

Issues and PRs are complementary:

- an issue owns a durable, independently reviewable capability, decision, defect, or discovery;
- a PR owns one concrete reviewable change and its proving tests;
- native GitHub parent/sub-issue, dependency, duplicate, lifecycle, assignee, milestone, and Development/closing semantics represent the work graph;
- labels are non-exclusive semantic tags, never workflow enums;
- stacked PRs are encouraged where dependency topology makes them useful.

Useful semantic labels may include `architecture`, `backend`, `frontend`, `database`, `data-generation`, `security`, `ai`, `rag`, `deployment`, `testing`, `documentation`, `observability`, `bug`, and `enhancement`.

Likely capability umbrellas include technical hollow mock-up, customer activity, operator identity, risk overview, AI analysis, and RAG. Their exact native work graph is intentionally deferred until the SRS/SDD/V&V preflight establishes authoritative scope.

Expect more than a handful of PRs. A plausible implementation may require roughly 12–14 small PRs, with stacks and parallel branches where appropriate, but fixed PR numbering must not substitute for the native graph or the actual design.

## 14. Review is continuous

Route review according to the change:

- SRS and assumptions: requirements + adversarial review;
- SDD and ADRs: architecture review;
- hollow mock-up: architecture + operations review;
- schema/persistence: architecture + verification review;
- security: adversarial review;
- risk behaviour: requirements + verification review;
- AI/RAG: adversarial + architecture review;
- tests: verification review;
- integrated delivery: final cross-system review.

Continue dependent work in stacks while parent review is active. Day 5 includes consolidation review, not the first serious review of the system. Formatting and lint belong to deterministic tools, not expensive AI reviewer attention.

## 15. Five-day execution envelope

| Day | Main objective | Required visible state by evening |
| --- | --- | --- |
| J1 | Work-graph/artifact preflight, SRS/SDD/V&V baseline, R0, first read slice | Live HTTPS URL; complete architecture shell; customer activity visible through stubs/synthetic data |
| J2 | Persistence, deterministic data, identity, risk | Real database; multiple operators; meaningful activity/risk dashboard |
| J3 | Structured AI stub, persistence/history, real RAG | Every explicitly requested final capability demonstrable without an external LLM dependency |
| J4 | Optional live provider, failure paths, test/architecture hardening, UX | Complete robust product; only optional work remains |
| J5 | Freeze, consolidation reviews, README/evidence, demo rehearsal | Exact deployed SHA ready for a practiced 10–15 minute demonstration |

Daily invariant:

> The currently deployed SHA must support a coherent demonstration of everything already claimed complete.

## 16. Stop rules

1. No unfinished SpecGraph Harness capability may block challenge delivery.
2. A framework integration consuming roughly 60–90 minutes without vertical progress triggers reevaluation or fallback.
3. No optional feature while a required delivery obligation remains red.
4. No new feature on day 5 except correction of a blocking defect.
5. Prefer deletion, substitution, or simplification over heroic custom infrastructure.
6. Prove deployment, health, and runner/CI execution on day 1.

## 17. Bounded SpecGraph usage

Use the mature engineering method:

- controlled specification/design/test authority;
- GitHub-native work graph;
- small/stacked PR discipline;
- reuse-first and hexagonal boundaries;
- bounded context supplied to implementation/review agents;
- deterministic checks before probabilistic review;
- generated PlantUML/human views;
- continuous review and retained evidence.

Do not place unfinished harness machinery on the critical path:

- no dependency on an unfinished requirements engine;
- no requirement for Java code-graph support;
- no requirement for generic reviewer orchestration;
- no unfinished harness UI/persistence;
- no runtime dependency from the Java product onto the Python harness.

The harness supplies the engineering method. The consumer repository owns the challenge-specific SRS, SDD, ADRs, implementation, tests, deployment, and evidence.

## 18. Success criterion

The desired final signal is not “a framework was built around the exercise”. It is:

> A small financial-services application can be run locally and reached on a real deployment; its behaviour traces back to explicit requirements and design decisions; AI is bounded behind replaceable interfaces and grounded by real retrieval; important claims are mechanically verified where possible; the GitHub history shows incremental reviewed delivery; and the whole system can be explained clearly in 10–15 minutes.

The next step after this inception plan is a fresh-context preflight: instantiate the native Swissquote work graph, produce the controlled SRS/SDD/ADR/V&V baseline with PlantUML views, review those artefacts, and immediately start the R0 hollow mock-up stack.