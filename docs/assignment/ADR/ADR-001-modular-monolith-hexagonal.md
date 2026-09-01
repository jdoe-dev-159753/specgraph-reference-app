# ADR-001 — Modular monolith with strict hexagonal boundaries

**Decision date:** 2026-08-30  
**Decision owner:** reference application design  
**Normative inputs:** `CAA-SRS-001`, `AGENTS.md`, inception reuse/pattern blueprint

## Context

The assignment must become a demonstrable application in five days while preserving enough architectural separation to replace stubs, persistence and AI providers incrementally. Splitting the product into independently deployed services would add network, deployment and consistency problems without a source requirement that needs them.

The SRS also requires provider-neutral analysis behaviour, deterministic verification and reproducible execution. Framework and infrastructure types therefore cannot become the durable application contract merely because they are convenient during implementation.

The inception blueprint additionally requires restrained use of established Gang of Four patterns where they arise naturally, rather than treating pattern names as architecture by themselves.

Spring Modulith's default detection also makes physical package topology architecturally significant: each direct sub-package under the Spring Boot application package is an application module unless a different detection strategy is explicitly selected. A new horizontal root package can therefore become a real fifth module even when its classes were intended to be only adapters.

## Decision

Use one deployable backend process organized as a modular monolith with strict hexagonal dependency direction.

The backend has exactly four application modules:

- `identity`;
- `customer`;
- `risk`;
- `analysis`.

The direct packages `dev.specgraph.reference.identity`, `.customer`, `.risk`, and `.analysis` are the Spring Modulith module bases. No horizontal top-level `web`, `persistence`, `infrastructure`, `common`, or `shared` package is an application module.

Public module contracts and small application/domain value types may live directly in the module base package. Framework-facing adapters live in a small number of descriptive sub-packages of the module that owns the use case or port, for example `customer.web` and `customer.persistence`. The design does **not** require ceremonial `application/domain/adapter/in/out` package depth when those namespaces do not provide a concrete visibility or ownership benefit.

This gives the physical package structure one job: enforce module ownership. Detailed hexagonal roles are expressed by project-owned ports/contracts, adapter types and the component/port views rather than by inventing a package for every architectural noun.

Domain/application code owns ports and durable contracts. Frameworks, databases, AI providers, vector retrieval, web transport and deployment products implement adapters around those ports.

Use Spring Modulith to make module boundaries visible and mechanically checkable. Cross-module access goes through explicit public application contracts rather than repository/row/framework leakage. Architecture verification must assert both:

1. `ApplicationModules.verify()` succeeds; and
2. the detected module identifiers are exactly `{identity, customer, risk, analysis}`.

The current customer snapshot includes the public `RiskEvidence` contract, so the accepted dependency direction is `customer -> risk`. The reverse dependency is prohibited unless a later explicit architecture decision restructures the contract boundary. Analysis may depend on the customer/risk-facing application contracts it needs for orchestration; infrastructure adapters must not reverse those dependencies.

### Pattern roles inside the architecture

Hexagonal architecture is the architectural style. The GoF patterns below are implementation/design roles used inside that style; they do not define the architecture themselves.

- **Adapter** — concrete infrastructure integrations adapt framework/provider/persistence APIs to project-owned ports. Examples include `SyntheticActivityAdapter`, `JdbcCustomerActivityAdapter`, `StaticPolicyAdapter`, `PgVectorPolicyAdapter`, `DeterministicAnalysisStub`, `SpringAiAnalysisAdapter`, and `JdbcAnalysisHistoryAdapter`.
- **Strategy** — where a port has interchangeable implementations, dependency injection/configuration selects one strategy without branching provider logic through the application core. The clearest examples are `AnalysisModelPort`, `PolicyKnowledgePort`, and the synthetic/JDBC alternatives behind `CustomerActivityPort`.
- **Facade / application service** — coarse-grained application use-case services expose operator-facing operations while coordinating several ports behind one application boundary. The analysis use case is the clearest example: it composes activity lookup, policy retrieval, model execution, validation, and history persistence without exposing that choreography to the HTTP adapter or UI.

These roles are deliberately sparse. Framework-supplied data-access primitives, dependency injection, and ordinary composition are not renamed as additional GoF patterns merely to decorate the design.

The relational access-layer choice is controlled separately by [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md). It changes the concrete persistence Adapter implementation without changing this ADR's port ownership or dependency direction.

## Consequences

- one application can be started, tested and deployed early;
- exactly four module roots remain mechanically visible rather than silently growing through incidental top-level packages;
- inbound and outbound adapters stay inside the module whose use case/port they serve;
- the package tree remains shallow unless deeper visibility boundaries earn their cost;
- stub adapters can be replaced without creating a parallel architecture;
- framework and provider choices remain peripheral to domain/application contracts;
- module-boundary tests become part of architecture verification;
- interchangeable infrastructure is selected behind ports rather than encoded as condition-heavy application logic;
- operator-facing adapters depend on application facades/use cases rather than coordinating persistence, retrieval and model integrations directly;
- the design can later split a module only if measured operational needs justify the cost.

The trade-off is that process-level isolation and independent service scaling are intentionally absent from the baseline, and the module base packages carry some public application/domain contracts instead of mirroring a textbook package-per-layer hierarchy.

## Alternatives not selected

### Microservices

Rejected for the assignment baseline because no requirement needs independent deployment or scaling and the operational overhead would consume delivery time while weakening the first vertical slice.

### Horizontal `web` / `persistence` application modules

Rejected because transport and persistence are adapter roles, not business capabilities. Making them direct Spring Modulith modules would invert ownership and encourage business modules to depend on infrastructure-shaped horizontal layers.

### Mandatory package-per-layer hierarchy

Rejected because hexagonal dependency direction is enforced through module ownership, ports, visibility and architecture verification. Creating `application/domain/adapter/in/out` namespaces everywhere would add ceremony and make the package diagram more impressive than the actual separation it buys.

### Layered monolith with framework-owned domain types

Rejected because it would make later adapter substitution and provider neutrality harder to verify.

### Pattern-driven class hierarchy

Rejected because the inception strategy explicitly prefers composition and restrained pattern use. Pattern names justify a role only when they clarify an actual substitution or orchestration boundary; they are not a reason to introduce speculative abstract classes or factories.

## Requirement and invariant links

Primary links: `FR-CUST-001`, `FR-ACT-001`, `FR-ACT-002`, `FR-RISK-001`, `FR-AI-001`, `FR-AI-002`, `FR-AUTH-001`, `FR-RAG-001`, `FR-HIST-001`, `FR-HIST-002`, `NFR-REP-001`, `NFR-VER-001`, `INV-AI-001`.
