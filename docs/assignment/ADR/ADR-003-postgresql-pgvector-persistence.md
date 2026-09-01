# ADR-003 — PostgreSQL with pgvector as the unified persistent store

**Decision date:** 2026-08-30  
**Decision owner:** persistence and knowledge adapters  
**Normative inputs:** `CAA-SRS-001`, supplied relational schema

## Context

The assignment is relational by construction and requires persisted activity, risk evidence and AI-analysis history. RAG additionally needs a retrievable policy corpus, but the source does not require a separate vector database.

For a five-day reference application, operating separate persistence products would create extra configuration, migrations, health checks and failure modes before those products provide user-visible value.

## Decision

Use PostgreSQL as the production-like relational store and enable pgvector for the RAG adapter.

Persistence remains behind project-owned ports. Flyway is the sole schema/migration authority. Project-owned relational adapters use the Spring JDBC access strategy selected by [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md); pgvector is used only by the policy-knowledge adapter.

The relational model contains:

- the supplied transaction/activity/risk structures;
- a minimal project-owned customer relation required to resolve the supplied foreign key;
- project-owned analysis-history persistence required by `FR-HIST-001`/`FR-HIST-002`;
- policy-document/chunk metadata and vector data required by the production-like RAG adapter.

Synthetic/static adapters remain valid for earlier delivery rings, so PostgreSQL is not allowed to become a prerequisite for domain tests.

## Consequences

- one production-like data service supports relational and vector needs;
- Flyway migrations provide an explicit schema history;
- Testcontainers can exercise real PostgreSQL semantics in integration tests;
- relational access does not grant an ORM authority over the Flyway-controlled schema;
- vector-store choice remains behind `PolicyKnowledgePort`;
- later replacement of pgvector does not change the analysis application contract.

The trade-off is that vector retrieval shares the lifecycle and scaling envelope of the relational database, which is acceptable for this assignment-sized workload.

## Alternatives not selected

### Separate vector database

Rejected because it adds an operational product without a source requirement or demonstrated scale need.

### In-memory database as the production-like target

Rejected because it weakens fidelity to PostgreSQL behaviour and the supplied relational constraints; in-memory fakes remain useful behind ports for earlier slices/tests.

### JPA/Hibernate as an implicit part of the store decision

Superseded by [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md). PostgreSQL/pgvector and relational access technology are separate architectural dimensions: this ADR owns the persistent product/topology decision, while ADR-007 owns how project adapters access relational data.

## Requirement and invariant links

Primary links: `FR-CUST-001`, `FR-ACT-001`, `FR-ACT-002`, `FR-RISK-001`, `FR-RAG-001`, `FR-HIST-001`, `FR-HIST-002`, `INV-DATA-001`, `INV-DATA-002`, `INV-RISK-001`, `INV-HIST-001`.
