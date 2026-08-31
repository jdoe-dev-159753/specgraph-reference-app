# ADR-002 — Provider-neutral analysis with deterministic baseline adapters

**Decision date:** 2026-08-30  
**Decision owner:** analysis boundary  
**Normative inputs:** `CAA-SRS-001`, `INV-AI-001`, `CON-AI-001`, `CON-AI-002`, inception reuse/pattern blueprint

## Context

The assignment requires AI-assisted analysis and RAG but explicitly permits stubbing actual LLM calls. The source does not mandate a provider or model and does not define whether customer/activity/policy content may be sent to an external service.

The same structured result contract must therefore remain usable for deterministic tests, offline demonstrations and optional live-provider experiments.

The inception blueprint calls out Strategy for interchangeable stub/live implementations, Adapter for infrastructure integrations behind application-owned ports, and Facade/application service for analysis orchestration. Those pattern roles are applied deliberately here rather than left implicit in class names.

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

### Pattern mapping

- **Strategy:** `AnalysisModelPort` defines the model-analysis strategy contract and `PolicyKnowledgePort` defines the policy-retrieval strategy contract. Dependency injection/profile configuration selects deterministic/static or live/vector implementations without changing the analysis use case.
- **Adapter:** `DeterministicAnalysisStub`, `SpringAiAnalysisAdapter`, `StaticPolicyAdapter`, and `PgVectorPolicyAdapter` adapt concrete execution technologies/data sources to those project-owned contracts.
- **Facade / application service:** the analysis application service coordinates customer context, policy retrieval, model strategy execution, structured-result validation, and history persistence as one coarse-grained use case. Provider-specific orchestration does not leak into the HTTP adapter or UI.

Strategy here is therefore one role *within* the hexagonal design, not the mechanism from which hexagonal architecture itself is constructed.

## Consequences

- mandatory acceptance and tests remain deterministic and provider-independent;
- live-provider integration can be added without changing application contracts;
- customer/policy data cannot leak merely because a provider dependency is present on the classpath;
- deterministic and production-like adapters exercise the same orchestration path;
- provider-specific features are available only through adapter capabilities, not leaked into the domain model;
- application orchestration remains one testable facade/use-case boundary regardless of which strategies are selected.

The trade-off is that provider-specific functionality must be translated through a narrower project-owned contract.

## Alternatives not selected

### Direct provider SDK usage from application services

Rejected because it couples domain/application behaviour to one provider and makes offline acceptance impossible.

### Mocking provider SDK classes in tests

Rejected because a mock of an external SDK does not prove the project's own stable boundary and encourages provider types to spread inward.

### Provider-specific application service variants

Rejected because separate orchestration services per provider would duplicate the use case and turn a Strategy/Adapter substitution seam into parallel application architectures.

## Requirement and invariant links

Primary links: `FR-AI-001`, `FR-AI-002`, `FR-RAG-001`, `NFR-REP-001`, `NFR-VER-001`, `CON-AI-001`, `CON-AI-002`, `INV-AI-001`.
