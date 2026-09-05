# ADR-004 — Use the preferred Java/Spring/React stack as assembled infrastructure

**Decision date:** 2026-08-30  
**Decision owner:** delivery shell and adapters  
**Normative inputs:** `CAA-SRS-001`, assignment technology preferences, `ADR-001`

## Context

The assignment explicitly names Java 17+, Spring Boot, Hibernate/JPA, React and a relational database as preferred technologies while leaving supporting technologies open. Using that ecosystem reduces reviewer surprise and lets the solution spend its differentiation budget on specification discipline, boundaries, traceability and AI integration rather than on defending an exotic stack.

Those technologies are preferences, not a requirement to retain every named framework when the concrete architecture does not use the service it provides. The repository also requires buy-and-assemble behaviour: mature framework capability should be reused instead of recreated as custom infrastructure, while overlapping framework layers should not be accumulated merely because each is individually mature.

## Decision

Use the following baseline composition:

### Backend

- Java 21;
- Spring Boot;
- Spring Modulith;
- Spring MVC;
- Spring Security;
- Jakarta Persistence with Hibernate ORM inside relational adapters, as decided by [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md);
- Flyway as the sole schema and migration authority;
- Spring AI only inside AI/vector adapters, with pgvector persistence kept separate from Hibernate/JPA;
- the repository-owned OpenAPI document as the sole HTTP-contract authority, rendered into reviewer documentation by a pinned build-time Redocly CLI container.

### Frontend

- React with TypeScript;
- MUI for application components/layout;
- TanStack Query for server-state fetching/caching.

### Verification and runtime

- Testcontainers for production-like integration dependencies;
- Docker Compose for the reproducible mandatory local topology.

The following map makes the service rendered by the browser/application stack explicit. "Runtime" means code that participates in the delivered application; "enforcement" and "tooling" dependencies support that architecture without becoming another deployed service.

| Technology | Service rendered and reuse rationale | Architectural boundary / role | Consequence of substitution |
| --- | --- | --- | --- |
| Spring Boot | Supplies the Java process shell, configuration/DI/autoconfiguration, embedded HTTP host and health endpoint instead of project-owned bootstrap and operational plumbing. | Server runtime shell around application ports and inbound/outbound adapters. | A replacement must rehost composition, configuration, HTTP/static delivery and health while preserving project-owned ports and domain contracts. |
| Spring Modulith | Discovers application modules and mechanically verifies their dependency graph through `ApplicationModules.verify()` instead of a custom architecture checker. | Build/test enforcement of the in-process modular-monolith boundary; not a network service. | Replace its verification and package conventions before removing it; external protocols and application ports need not change. |
| React | Supplies browser component composition, rendering and interaction lifecycle instead of a custom UI runtime. | Client runtime presentation layer consuming the `/api/*` boundary. | Rewrite browser composition/bootstrap while retaining the REST and domain contracts. |
| TypeScript | Statically checks frontend application code through `tsc --noEmit` instead of project-owned type validation. | Compile-time tooling; erased from delivered browser assets. | Rewrite or revalidate frontend source and its build gate; server/runtime boundaries remain unchanged. |
| MUI | Supplies commodity controls, layout and theming primitives instead of maintaining a bespoke component kit. | Client runtime presentation components; no domain or transport authority. | Replace presentation components/theme while retaining React workflow state and API contracts. |
| TanStack Query | Supplies server-state fetch, cache, mutation and invalidation semantics instead of custom request/cache coordination. | Client runtime adapter between React workflows and same-origin REST endpoints. | Replace query/mutation orchestration while preserving endpoint schemas and domain semantics. |
| Node.js | Executes npm, TypeScript and Vite in development and the container build stage. | Build/development tool runtime; absent from the final Java runtime image. | Rehost the frontend toolchain and update CI/container build recipes; the executable JAR contract is unchanged. |
| Vite | Builds browser assets and supplies the development server plus `/api` proxy instead of project-owned bundling/dev-server code. The delivered demo image consumes only its static build output. | Build/development tooling, not a durable deployed service. | Replace package scripts, build configuration and development proxy; Spring Boot's same-origin runtime topology remains unchanged. |
| OpenAPI + Redocly CLI | Keeps endpoint and schema semantics in one maintained contract and renders an offline browsable reviewer view without project-owned documentation rendering code. | `openapi.yaml` is authoritative; the pinned Redocly container is build-only tooling and generated HTML is non-authoritative. | A replacement renderer must consume the same OpenAPI file without generating or competing for contract ownership. |

Framework types stay outside durable domain/application contracts. The selected libraries supply commodity capability; project-owned code focuses on domain composition, ports and assignment-specific behaviour.

The relational access subdecision was refined after the accepted R2 Spring JDBC baseline. [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md) selects Jakarta Persistence with Hibernate ORM for the final relational adapters while preserving project-owned ports and values. Flyway remains the sole schema and migration authority, and the Spring AI pgvector adapter remains a separate persistence concern outside Hibernate/JPA.

Remote-demo ingress, TLS termination, reverse proxies, load balancers, hosting products and public DNS are deliberately **not** selected by this ADR. The source does not prescribe an external deployment target, and `AMB-DEP-001` leaves that choice unresolved. If a concrete remote demonstration later requires edge infrastructure, it must be selected from the actual deployment constraints rather than precommitted here.

## Consequences

- the implementation follows the assignment's preferred ecosystem without pretending those preferences are functional requirements;
- common security, relational access, HTTP, UI, API-documentation and container concerns are reused rather than reimplemented;
- the deployed `openapi.yaml` remains the single HTTP-contract authority, while generated API-reference HTML is a disposable reviewer view;
- the relational stack reuses Jakarta Persistence with Hibernate ORM behind project-owned ports while Flyway alone owns the schema;
- Spring AI pgvector retrieval remains separate from the Hibernate/JPA business-persistence adapters;
- Java 21 remains within the source's Java 17+ preference;
- the mandatory application topology is reproducible locally through Compose;
- no edge component becomes an architectural dependency before a remote deployment target exists.

The trade-off is a moderately broad dependency surface, controlled by requiring every non-trivial dependency to render a concrete durable service. A framework is not kept merely because it appeared in the initial baseline or assignment preference list.

## Alternatives not selected

### Custom UI/component stack or hand-written persistence/security infrastructure

Rejected because it adds code without increasing assignment value. Hibernate/JPA, Spring Security and the selected browser libraries supply the mature infrastructure while project code owns domain-specific composition and mappings.

### Keep Spring JDBC as the relational baseline

Not selected for the final implementation. [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md) records the Hibernate/JPA substitution and its verification constraints.

### Kubernetes as the baseline runtime

Rejected because the assignment has no orchestration requirement and Compose is sufficient for the intended topology.

### Preselect a reverse proxy for a hypothetical remote demo

Deferred rather than selected. A reverse proxy may later be useful for TLS termination, routing or load balancing, but none of those concerns is part of the mandatory local baseline and the remote deployment target is unresolved.

### Generate a second OpenAPI document from Spring controller annotations

Not selected. Adding `springdoc-openapi` beside the maintained and contract-tested `openapi.yaml` would create two candidate descriptions of the same HTTP boundary. The build instead renders the maintained document directly; changing contract ownership would require a separate explicit decision and migration of the existing contract checks.

## Requirement and constraint links

Primary links: `FR-AUTH-001`, `FR-HIST-001`, `FR-HIST-002`, `FR-RAG-001`, `NFR-REP-001`, `NFR-VER-001`, `NFR-SEC-001`, `CON-AI-001`, `CON-AI-002`.
