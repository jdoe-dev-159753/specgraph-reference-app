# ADR-007 — Select Hibernate/JPA for relational persistence adapters

**Decision date:** 2026-09-04
**Decision owner:** relational persistence adapters
**Normative inputs:** `CAA-SRS-001`, supplied relational schema, `ADR-001`, `ADR-003`, `ADR-004`

## Context

R2 replaces the deterministic synthetic customer-activity source with the supplied PostgreSQL-shaped relational model while preserving the project-owned `CustomerActivityPort` and `CustomerReviewQueryPort`. R3 adds persisted analysis history behind `AnalysisHistoryPort`. R4 adds pgvector-backed policy retrieval behind the separate `PolicyKnowledgePort`.

The first accepted relational implementation used Spring JDBC and explicit SQL. A later design direction proposed replacing that query layer with jOOQ. The final delivery instead selects the mature Hibernate ORM implementation of Jakarta Persistence named by the assignment. One relational persistence model is easier to explain, verify and maintain than a permanent mixture of JDBC, jOOQ and ORM adapters.

The supplied schema remains authoritative and source-shaped. Selecting an ORM does not transfer schema ownership to Hibernate, turn persistence entities into domain contracts, or require the application core to adopt entity lifecycle semantics.

## Decision

Use **Jakarta Persistence with Hibernate ORM** as the single relational persistence technology family for application-owned PostgreSQL adapters.

The design retains separate adapters for separate project-owned ports:

- `JpaCustomerActivityAdapter` implements `CustomerActivityPort` and `CustomerReviewQueryPort`;
- `JpaAnalysisHistoryAdapter` implements `AnalysisHistoryPort`.

They share one Hibernate/JPA infrastructure but do not become one cross-module persistence facade. The existing ports, commands and returned project-owned values remain unchanged. Jakarta Persistence annotations, `EntityManager`, Hibernate types, persistence entities and repository helpers stay inside outbound adapter packages.

### Schema authority

Flyway is the **sole schema and migration authority**. Hibernate must never create, update or migrate the schema:

- Flyway migrations run before relational adapters accept traffic;
- Hibernate schema handling is limited to `validate` for executable compatibility checking;
- `create`, `create-drop` and `update` are forbidden outside disposable experiments and are not repository runtime modes;
- JPA mappings conform to the Flyway-owned PostgreSQL names, nullability, lengths, precision, scale, keys and relationships.

Hibernate validation is evidence that mappings agree with the migrated schema, not a second source of schema truth.

### Exact decimal semantics

All SQL `NUMERIC`/`DECIMAL` values remain exact:

- `DECIMAL(18,2)` activity amounts map to `BigDecimal` with explicit precision `18` and scale `2`;
- `DECIMAL(5,2)` risk score contributions and adapter-local rule weights map to `BigDecimal` with explicit precision `5` and scale `2`;
- no relational mapping or projection may use `float` or `double` for those values;
- currency remains a separate value and is never inferred from scale;
- mapping into the existing project-owned `Activity` and `RiskEvidence` contracts preserves `BigDecimal` without lossy conversion;
- the HTTP/OpenAPI boundary continues to expose exact decimal strings where JSON number semantics could imply binary floating point.

Database-backed contract tests must compare exact values and scale-sensitive serialization where the public contract fixes scale.

### Query and transaction boundary

Hibernate/JPA owns relational query execution, parameter binding, pagination and persistence for customer activity, customer review and analysis history. JPQL, Criteria or adapter-private projections may be used where they keep the mapping clear. A bounded Hibernate native query is acceptable only for a demonstrated PostgreSQL-specific residual gap; it remains part of the JPA adapter and must not introduce a parallel Spring JDBC or jOOQ runtime path.

Complete multi-query customer snapshots retain one consistent PostgreSQL transaction snapshot. Bounded customer-review and analysis-history queries continue to apply filtering, stable ordering, counts and pagination in the database before mapping results into project-owned page contracts.

### pgvector remains separate

`PgVectorPolicyAdapter` remains a separate outbound adapter behind `PolicyKnowledgePort`. Its Spring AI `PgVectorStore`, local embeddings, vector types and retrieval semantics do not become JPA entities or Hibernate repositories. Flyway still owns the `vector` extension and `policy_vector_store` table. Sharing the same PostgreSQL service does not merge relational business persistence with vector retrieval semantics.

### Migration boundary

The accepted Spring JDBC adapters remain historical implementation evidence. Issue #164 completed the substitution with Hibernate/JPA rather than retaining selectable JDBC and JPA alternatives. In the resulting implementation:

- no Spring JDBC or jOOQ dependency is retained for application relational adapters without a separately demonstrated owner;
- old JDBC adapter classes are removed, and the same port contracts pass against JPA;
- repository runtime configuration exposes one relational adapter family, not a new end-user selector;
- PostgreSQL/Flyway/Testcontainers evidence remains mandatory.

## Alternatives considered

### Keep Spring JDBC

Not selected for the final implementation. It keeps SQL explicit but leaves query text, row mapping and association assembly largely project-owned. The final delivery prefers the mature ORM requested by the assignment while keeping it confined behind the same ports.

### Replace JDBC with jOOQ

Not selected. jOOQ offers a mature typed SQL DSL, but adding generated query types alongside Hibernate/JPA would create two relational models. The former jOOQ direction in this ADR is superseded.

### Spring Data JDBC or raw JDBC

Not selected. Spring Data JDBC introduces another aggregate model, while raw JDBC reintroduces solved resource, transaction and exception-handling plumbing.

### Let Hibernate generate the schema

Rejected. It would compete with the supplied schema and Flyway migrations, weaken reviewability and make environment-specific ORM inference part of the data contract.

### Map pgvector through Hibernate

Rejected for this delivery. Policy retrieval has a separate port, evidence model and Spring AI adapter. Moving it into the relational ORM would couple independent semantics without removing a demonstrated gap.

## Consequences

- Hibernate/JPA is the only application relational persistence technology;
- Flyway remains the only DDL/migration authority;
- schema validation fails fast when mappings drift from the migrated PostgreSQL schema;
- exact decimal behavior is explicit in mappings, project-owned `BigDecimal` contracts and database-backed tests;
- application ports and domain/application values do not depend on ORM types;
- customer activity and analysis history retain separate adapters and module ownership while sharing one persistence technology;
- pgvector retrieval remains independently replaceable behind `PolicyKnowledgePort`;
- ORM convenience must not create unbounded collection loading, N+1 query behavior or lazy proxies crossing the adapter boundary;
- the migration removes the transitional JDBC implementation rather than preserving two production paths.

## Verification consequences

- unchanged port-contract suites must pass against `JpaCustomerActivityAdapter` and `JpaAnalysisHistoryAdapter`;
- PostgreSQL Testcontainers must run Flyway first and Hibernate schema validation second;
- integration tests must cover complete snapshot consistency, source-shaped joins, bounded filtering/count/pagination and persisted analysis-history attribution;
- exact `DECIMAL(18,2)` and `DECIMAL(5,2)` values must round-trip as `BigDecimal` with no binary floating-point conversion;
- architecture tests must reject Jakarta Persistence, Hibernate and persistence-entity types outside adapter/infrastructure packages and project-owned port implementations;
- query-count or equivalent integration evidence must prevent N+1 loading on bounded and complete customer paths;
- pgvector verification continues through `PgVectorPolicyAdapter` with Flyway-owned vector schema and real PostgreSQL/pgvector integration;
- a static dependency ratchet must reject reintroduction of parallel Spring JDBC or jOOQ application adapters after migration.

## Requirement and invariant links

Primary links: `FR-CUST-001`, `FR-ACT-001`, `FR-ACT-002`, `FR-RISK-001`, `FR-HIST-001`, `FR-HIST-002`, `INV-DATA-001`, `INV-DATA-002`, `INV-RISK-001`, `INV-HIST-001`, `NFR-VER-001`.
