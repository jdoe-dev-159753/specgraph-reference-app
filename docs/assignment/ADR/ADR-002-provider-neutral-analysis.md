# ADR-002 — Provider-neutral analysis with deterministic baseline adapters

**Decision date:** 2026-08-30  
**Decision owner:** analysis boundary  
**Normative inputs:** `CAA-SRS-001`, `INV-AI-001`, `CON-AI-001`, `CON-AI-002`

## Context

The assignment requires AI-assisted analysis and RAG but explicitly permits stubbing actual LLM calls. The source does not mandate a provider or model and does not define whether customer/activity/policy content may be sent to an external service.

The same structured result contract must therefore remain usable for deterministic tests, offline demonstrations and optional live-provider experiments.

## Decision

The application owns two provider-neutral outbound ports:

- `AnalysisModelPort` for producing the structured risk analysis;
- `PolicyKnowledgePort` for retrieving relevant policy/knowledge evidence.

The mandatory baseline implementations are:

- `DeterministicAnalysisStub` behind `AnalysisModelPort`;
- `StaticPolicyAdapter` behind `PolicyKnowledgePort`.

Later adapters may include:

- `SpringAiAnalysisAdapter` for a configured external or local model provider;
- `PgVectorPolicyAdapter` for vector retrieval from PostgreSQL/pgvector.

All analysis adapters return the same project-owned structured result: risk level, findings summary and recommendations. Retrieved evidence uses project-owned provenance metadata rather than provider SDK types.

External-provider configuration is opt-in. The default execution and verification path makes no external model call.

## Consequences

- mandatory acceptance and tests remain deterministic and provider-independent;
- live-provider integration can be added without changing application contracts;
- customer/policy data cannot leak merely because a provider dependency is present on the classpath;
- deterministic and production-like adapters exercise the same orchestration path;
- provider-specific features are available only through adapter capabilities, not leaked into the domain model.

The trade-off is that provider-specific functionality must be translated through a narrower project-owned contract.

## Alternatives not selected

### Direct provider SDK usage from application services

Rejected because it couples domain/application behaviour to one provider and makes offline acceptance impossible.

### Mocking provider SDK classes in tests

Rejected because a mock of an external SDK does not prove the project's own stable boundary and encourages provider types to spread inward.

## Requirement and invariant links

Primary links: `FR-AI-001`, `FR-AI-002`, `FR-RAG-001`, `NFR-REP-001`, `NFR-VER-001`, `CON-AI-001`, `CON-AI-002`, `INV-AI-001`.
