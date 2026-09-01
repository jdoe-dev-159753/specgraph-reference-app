# ADR-007 — Use Spring JDBC for explicit relational adapters

**Decision date:** 2026-09-01  
**Decision owner:** relational persistence adapters  
**Normative inputs:** `CAA-SRS-001`, supplied relational schema, `ADR-001`, `ADR-003`, `ADR-004`

## Context

R2 replaces the deterministic synthetic customer-activity source with the supplied PostgreSQL-shaped relational model while preserving the project-owned `CustomerActivityPort`. Later rings add persisted analysis history and pgvector-backed policy retrieval behind their own project-owned ports.

The first R2 implementation used Spring Data JPA/Hibernate because Hibernate/JPA is named as a preferred technology in the assignment and is the conventional Spring relational default. Exact-head PostgreSQL/Testcontainers verification then exposed a Hibernate schema-validation mismatch for exact decimal columns, but the more important architectural observation is broader than that defect: the R2 adapter already performs explicit query orchestration and explicit mapping across customer, transaction-specialization and risk tables while using little of the persistence-context, lazy-loading, dirty-tracking, cascade or object-graph lifecycle machinery that distinguishes a full ORM.

The selected architecture is reuse-first, but reuse does not mean adding an abstraction merely because it is mature. The dependency surface should contain the smallest set of broad, well-supported frameworks that actually render durable services to the application. Flyway is already the authority for schema evolution, PostgreSQL is already the production-like store, and Spring already owns dependency injection, transaction management, configuration and JDBC infrastructure.

## Decision

For the **R2 delivery baseline**, use **Spring Framework JDBC**, with `JdbcClient` as the default relational access API and `JdbcTemplate`/named-parameter support only where `JdbcClient` does not cover a concrete need, for project-owned relational persistence adapters.

The R2 application therefore uses this persistence composition:

- PostgreSQL owns relational storage and PostgreSQL-specific query semantics;
- Flyway is the sole schema and migration authority;
- Spring Boot configures the datasource, transactions and JDBC infrastructure;
- Spring Framework `JdbcClient` executes explicit SQL and maps database rows inside outbound adapters;
- the PostgreSQL JDBC driver provides the wire-level database integration;
- project-owned ports and contracts remain independent of Spring JDBC, SQL and PostgreSQL types.

The R2 activity implementation is `JdbcCustomerActivityAdapter` behind `CustomerActivityPort`. This explicit-SQL implementation is a bounded delivery bridge, not the desired permanent query-construction style.

Issue #164 owns the mandatory hardening follow-up that replaces hand-written runtime persistence SQL with a mature type-safe jOOQ query layer behind the same hexagonal ports. That migration must preserve Flyway as schema authority, exact decimal semantics, PostgreSQL Testcontainers evidence and the existing application/domain contracts. jOOQ is intended to **replace** the hand-written query layer, not coexist indefinitely as a second competing relational model.

`BigDecimal` remains the Java representation for exact decimal values. SQL `NUMERIC`/`DECIMAL` remains exact in PostgreSQL, and the HTTP/OpenAPI boundary may choose an exact decimal string where the reviewer contract must not imply binary floating-point semantics. No ORM type inference is allowed to redefine the controlled schema.

### Scope of the decision

This ADR selects the R2 access layer for project-owned relational adapters. It does not replace [`ADR-003`](ADR-003-postgresql-pgvector-persistence.md), whose durable decision is PostgreSQL plus pgvector as the unified persistent store, and it does not replace [`ADR-004`](ADR-004-baseline-web-stack.md), whose durable decision is the broader Java/Spring/React ecosystem. It supersedes only their earlier implicit choice of JPA/Hibernate for relational adapter implementation.

The pgvector-backed `PolicyKnowledgePort` remains a separate adapter concern. It may use a mature Spring AI vector-store facility or the later jOOQ-based relational layer if that materially reduces custom retrieval code while preserving the same application-owned port. This ADR does not force the R4 pgvector implementation before its concrete query needs exist.

## Why this is the minimal mature R2 stack

`JdbcClient` keeps R2 inside the already-selected Spring platform while removing an additional persistence model and lifecycle. Spring continues to solve commodity concerns such as datasource configuration, transaction demarcation, resource handling, parameter binding and exception translation. Project code temporarily owns explicit source-shaped SQL and assignment-specific assembly from rows into application contracts.

This is intentionally different from raw JDBC: connection/resource/error plumbing is not reimplemented by the project.

It is also intentionally different from a full ORM: the current source-shaped, join-oriented schema is not treated as an object graph whose lifecycle should be managed by a persistence context.

The remaining hand-written query construction is explicitly recognized as transitional debt. The project does not intend to grow home-made query builders, binding helpers or generic mapping infrastructure around it; #164 replaces that layer with jOOQ during hardening.

## Alternatives considered

### Spring Data JPA / Hibernate

Not selected for the current workload. It is mature and would be appropriate if the application needed persistence-context identity, dirty tracking, lazy loading, cascaded object-graph lifecycle management or substantial aggregate mutation through mapped entities. The current R2 read path does not use those services: it explicitly coordinates queries and explicitly maps source-shaped rows into project-owned values. Hibernate therefore adds mapping/schema interpretation and runtime lifecycle semantics without removing enough assignment-specific mapping code to justify that layer.

The exact-decimal schema-validation failure discovered in #159 is evidence of the additional interpretation layer, but it is not by itself the reason for this decision.

### Spring Data JDBC

Not selected as the baseline. It is simpler than JPA and remains a viable Spring technology when aggregate-root/repository semantics match the model. The supplied activity/risk schema is source-shaped and join-oriented rather than designed around Spring Data JDBC aggregate ownership. Adding repository/aggregate semantics merely to avoid writing explicit queries would add another conceptual layer without reducing the difficult part of the mapping.

A later bounded aggregate-shaped persistence need may justify Spring Data JDBC, but such an addition must earn a concrete service rather than becoming a second default persistence model.

### jOOQ

Not included in the R2 implementation because the immediate goal is to complete the relational substitution behind an already-stable port without expanding the ring. **Accepted as the intended J4 hardening successor for hand-written runtime SQL in #164.** jOOQ provides the type-safe SQL DSL, schema-derived generation, mature binding/mapping support and PostgreSQL dialect capability that should prevent the bounded R2 text-query bridge from becoming a project-owned SQL framework.

The jOOQ migration must be design-first: update or supersede the relevant access-layer decision and propagate SDD/design-map/V&V semantics before changing the adapter implementation, then prove contract equivalence with PostgreSQL Testcontainers.

### MyBatis

Not selected. It provides explicit SQL and mapping but currently offers no material service beyond what the selected R2 Spring JDBC platform already provides, while #164 already owns the intended type-safe hardening direction.

### Raw JDBC

Rejected because it would move solved connection, resource, transaction and exception-handling plumbing into project-owned code.

## Consequences

- the relational schema has one authority: Flyway migrations;
- PostgreSQL types and SQL are visible and directly reviewable at the R2 adapter boundary;
- the application removes JPA entity classes, Hibernate runtime semantics and Hibernate-specific annotations from the R2 path;
- exact decimal behavior is verified against PostgreSQL rather than inferred through ORM metadata;
- a bounded amount of SQL and row-to-contract assembly remains explicit in R2, confined to outbound adapters and covered by database-backed tests;
- that explicit query text is transitional debt with a named MUST_HAVE owner, #164, rather than an endorsed long-term style;
- R2 activity reads use Spring transaction/JDBC infrastructure without creating a second schema authority;
- jOOQ must replace, not duplicate, the hand-written query-construction layer when #164 is implemented;
- no project-specific query-builder/binding framework should be grown around the transitional JDBC code;
- Testcontainers continues to verify real PostgreSQL behavior behind stable ports before and after the later query-layer substitution.

The main R2 trade-off is less ORM automation in exchange for immediate schema fidelity and a narrow integration surface. The later hardening trade-off is different: once the relational query surface is stable enough, jOOQ is preferred over retaining or expanding hand-written SQL because it reuses mature typed query, binding and dialect machinery.

## Verification consequences

- `CustomerActivityPort` contract tests must pass unchanged when `SyntheticActivityAdapter` is replaced by `JdbcCustomerActivityAdapter`;
- PostgreSQL Testcontainers tests must verify source-shaped joins, exact `NUMERIC`/`DECIMAL` values, currency separation and risk-evidence mapping;
- architecture tests must prevent Spring JDBC/SQL classes from leaking into project-owned domain/application contracts;
- Flyway migration validation plus database-backed contract tests replace Hibernate schema validation as the executable check that migrated schema and adapter expectations agree;
- #164 must preserve the same port-level and PostgreSQL-backed evidence when the query implementation moves to jOOQ;
- after #164, a static/architecture ratchet must reject new hand-written runtime SQL text blocks or home-grown SQL-builder abstractions in application persistence adapters.

## Requirement and invariant links

Primary links: `FR-CUST-001`, `FR-ACT-001`, `FR-ACT-002`, `FR-RISK-001`, `FR-HIST-001`, `FR-HIST-002`, `INV-DATA-001`, `INV-DATA-002`, `INV-RISK-001`, `INV-HIST-001`, `NFR-VER-001`.
