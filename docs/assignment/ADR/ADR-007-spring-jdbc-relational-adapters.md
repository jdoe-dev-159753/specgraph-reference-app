# ADR-007 — Use Spring JDBC for explicit relational adapters

**Decision date:** 2026-09-01  
**Decision owner:** relational persistence adapters  
**Normative inputs:** `CAA-SRS-001`, supplied relational schema, `ADR-001`, `ADR-003`, `ADR-004`

## Context

R2 replaces the deterministic synthetic customer-activity source with the supplied PostgreSQL-shaped relational model while preserving the project-owned `CustomerActivityPort`. Later rings add persisted analysis history and pgvector-backed policy retrieval behind their own project-owned ports.

The first R2 implementation used Spring Data JPA/Hibernate because Hibernate/JPA is named as a preferred technology in the assignment and is the conventional Spring relational default. Exact-head PostgreSQL/Testcontainers verification then exposed a Hibernate schema-validation mismatch for exact decimal columns, but the more important architectural observation is broader than that defect: the R2 adapter already performs explicit query orchestration and explicit mapping across customer, transaction-specialization and risk tables while using little of the persistence-context, lazy-loading, dirty-tracking, cascade or object-graph lifecycle machinery that distinguishes a full ORM.

The selected architecture is reuse-first, but reuse does not mean adding an abstraction merely because it is mature. The dependency surface should contain the smallest set of broad, well-supported frameworks that actually render durable services to the application. Flyway is already the authority for schema evolution, PostgreSQL is already the production-like store, and Spring already owns dependency injection, transaction management, configuration and JDBC infrastructure.

## Decision

Use **Spring Framework JDBC**, with `JdbcClient` as the default relational access API and `JdbcTemplate`/named-parameter support only where `JdbcClient` does not cover a concrete need, for project-owned relational persistence adapters.

The application therefore uses this persistence composition:

- PostgreSQL owns relational storage and PostgreSQL-specific query semantics;
- Flyway is the sole schema and migration authority;
- Spring Boot configures the datasource, transactions and JDBC infrastructure;
- Spring Framework `JdbcClient` executes explicit SQL and maps database rows inside outbound adapters;
- the PostgreSQL JDBC driver provides the wire-level database integration;
- project-owned ports and contracts remain independent of Spring JDBC, SQL and PostgreSQL types.

The R2 activity implementation is `JdbcCustomerActivityAdapter` behind `CustomerActivityPort`. The later analysis-history implementation is `JdbcAnalysisHistoryAdapter` behind `AnalysisHistoryPort` unless a later accepted workload demonstrates a materially different need.

`BigDecimal` remains the Java representation for exact decimal values. SQL `NUMERIC`/`DECIMAL` remains exact in PostgreSQL, and the HTTP/OpenAPI boundary may choose an exact decimal string where the reviewer contract must not imply binary floating-point semantics. No ORM type inference is allowed to redefine the controlled schema.

### Scope of the decision

This ADR selects the access layer for project-owned relational adapters. It does not replace [`ADR-003`](ADR-003-postgresql-pgvector-persistence.md), whose durable decision is PostgreSQL plus pgvector as the unified persistent store, and it does not replace [`ADR-004`](ADR-004-baseline-web-stack.md), whose durable decision is the broader Java/Spring/React ecosystem. It supersedes only their earlier implicit choice of JPA/Hibernate for relational adapter implementation.

The pgvector-backed `PolicyKnowledgePort` remains a separate adapter concern. It may use explicit Spring JDBC/PostgreSQL queries where that is the smallest implementation, or a mature Spring AI vector-store facility if that facility materially reduces custom retrieval code while preserving the same application-owned port. This ADR does not force an R4 implementation choice prematurely.

## Why this is the minimal mature stack

`JdbcClient` keeps the application inside the already-selected Spring platform while removing an additional persistence model and lifecycle. Spring continues to solve commodity concerns such as datasource configuration, transaction demarcation, resource handling, parameter binding, exception translation and row-mapping infrastructure. Project code owns only the SQL and the assignment-specific assembly from source rows into application contracts.

This is intentionally different from raw JDBC: connection/resource/error plumbing is not reimplemented by the project.

It is also intentionally different from a full ORM: the current source-shaped, join-oriented schema is not treated as an object graph whose lifecycle should be managed by a persistence context.

## Alternatives considered

### Spring Data JPA / Hibernate

Not selected for the current workload. It is mature and would be appropriate if the application needed persistence-context identity, dirty tracking, lazy loading, cascaded object-graph lifecycle management or substantial aggregate mutation through mapped entities. The current R2 read path does not use those services: it explicitly coordinates queries and explicitly maps source-shaped rows into project-owned values. Hibernate therefore adds mapping/schema interpretation and runtime lifecycle semantics without removing enough assignment-specific mapping code to justify that layer.

The exact-decimal schema-validation failure discovered in #159 is evidence of the additional interpretation layer, but it is not by itself the reason for this decision.

### Spring Data JDBC

Not selected as the baseline. It is simpler than JPA and remains a viable Spring technology when aggregate-root/repository semantics match the model. The supplied activity/risk schema is source-shaped and join-oriented rather than designed around Spring Data JDBC aggregate ownership. Adding repository/aggregate semantics merely to avoid writing explicit queries would add another conceptual layer without reducing the difficult part of the mapping.

A later bounded aggregate-shaped persistence need may justify Spring Data JDBC, but such an addition must earn a concrete service rather than becoming a second default persistence model.

### jOOQ

Deferred. jOOQ provides excellent type-safe SQL, schema-derived code generation and a rich SQL DSL. It becomes attractive if accepted work grows into enough complex SQL that compile-time query/schema integration materially reduces risk. The current workload does not yet justify the additional DSL, code-generation and build surface.

### MyBatis

Not selected. It provides explicit SQL and mapping but currently offers no material service beyond what the selected Spring JDBC platform already provides for this application.

### Raw JDBC

Rejected because it would move solved connection, resource, transaction and exception-handling plumbing into project-owned code.

## Consequences

- the relational schema has one authority: Flyway migrations;
- PostgreSQL types and SQL are visible and directly reviewable at the adapter boundary;
- the application removes JPA entity classes, Hibernate runtime semantics and Hibernate-specific annotations from the R2 path;
- exact decimal behavior is verified against PostgreSQL rather than inferred through ORM metadata;
- some SQL and row-to-contract assembly remain explicit project code, but that code is confined to outbound adapters and expresses the source-to-application mapping the project must own anyway;
- R2 activity reads, later analysis-history persistence and future bounded filtering/pagination use the same Spring transaction/JDBC platform unless a concrete requirement justifies another tool;
- adding a second relational framework requires an explicit service that Spring JDBC does not already provide sufficiently;
- Testcontainers continues to verify real PostgreSQL behavior behind the stable ports.

The main trade-off is less ORM automation. For this source-shaped analytical workload, explicitness is preferred because it reduces hidden behavior and keeps framework boundaries narrow.

## Verification consequences

- `CustomerActivityPort` contract tests must pass unchanged when `SyntheticActivityAdapter` is replaced by `JdbcCustomerActivityAdapter`;
- PostgreSQL Testcontainers tests must verify source-shaped joins, exact `NUMERIC`/`DECIMAL` values, currency separation and risk-evidence mapping;
- architecture tests must prevent Spring JDBC/SQL classes from leaking into project-owned domain/application contracts;
- Flyway migration validation plus database-backed contract tests replace Hibernate schema validation as the executable check that migrated schema and adapter expectations agree;
- later history and pgvector adapters must receive equivalent database-backed verification behind their own ports.

## Requirement and invariant links

Primary links: `FR-CUST-001`, `FR-ACT-001`, `FR-ACT-002`, `FR-RISK-001`, `FR-HIST-001`, `FR-HIST-002`, `INV-DATA-001`, `INV-DATA-002`, `INV-RISK-001`, `INV-HIST-001`, `NFR-VER-001`.
