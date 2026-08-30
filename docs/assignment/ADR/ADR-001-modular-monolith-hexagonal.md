# ADR-001 — Modular monolith with strict hexagonal boundaries

**Decision date:** 2026-08-30  
**Decision owner:** reference application design  
**Normative inputs:** `CAA-SRS-001`, `AGENTS.md`

## Context

The assignment must become a demonstrable application in five days while preserving enough architectural separation to replace stubs, persistence and AI providers incrementally. Splitting the product into independently deployed services would add network, deployment and consistency problems without a source requirement that needs them.

The SRS also requires provider-neutral analysis behaviour, deterministic verification and reproducible execution. Framework and infrastructure types therefore cannot become the durable application contract merely because they are convenient during implementation.

## Decision

Use one deployable backend process organized as a modular monolith with strict hexagonal dependency direction.

The backend is divided into the application modules:

- `identity`;
- `customer`;
- `risk`;
- `analysis`.

Domain/application code owns ports and durable contracts. Frameworks, databases, AI providers, vector retrieval, web transport and deployment products implement adapters around those ports.

Use Spring Modulith to make module boundaries visible and mechanically checkable where practical. Cross-module access goes through explicit application contracts rather than repository/entity leakage.

## Consequences

- one application can be started, tested and deployed early;
- stub adapters can be replaced without creating a parallel architecture;
- framework and provider choices remain peripheral to domain/application contracts;
- module-boundary tests become part of architecture verification;
- the design can later split a module only if measured operational needs justify the cost.

The trade-off is that process-level isolation and independent service scaling are intentionally absent from the baseline.

## Alternatives not selected

### Microservices

Rejected for the assignment baseline because no requirement needs independent deployment or scaling and the operational overhead would consume delivery time while weakening the first vertical slice.

### Layered monolith with framework-owned domain types

Rejected because it would make later adapter substitution and provider neutrality harder to verify.

## Requirement and invariant links

Primary links: `FR-CUST-001`, `FR-ACT-001`, `FR-ACT-002`, `FR-RISK-001`, `FR-AI-001`, `FR-AI-002`, `FR-AUTH-001`, `FR-RAG-001`, `FR-HIST-001`, `FR-HIST-002`, `NFR-REP-001`, `NFR-VER-001`, `INV-AI-001`.
