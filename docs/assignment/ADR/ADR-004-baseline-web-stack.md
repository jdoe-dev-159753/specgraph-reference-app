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
- Spring Framework JDBC (`JdbcClient`) for explicit relational adapters, as decided by [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md);
- Flyway;
- Spring AI only inside AI/vector adapters;
- springdoc-openapi for generated API documentation.

### Frontend

- React with TypeScript;
- MUI for application components/layout;
- TanStack Query for server-state fetching/caching.

### Verification and runtime

- Testcontainers for production-like integration dependencies;
- Docker Compose for the reproducible mandatory local topology.

Framework types stay outside durable domain/application contracts. The selected libraries supply commodity capability; project-owned code focuses on domain composition, ports and assignment-specific behaviour.

The relational access subdecision was refined after R2 implementation evidence showed that the source-shaped activity/risk workload used explicit query orchestration and mapping but did not use the persistence-context, lazy-loading, dirty-tracking or object-graph lifecycle services that would justify retaining Hibernate/JPA. [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md) therefore supersedes only that earlier access-layer choice while keeping the solution inside the preferred Java/Spring/PostgreSQL ecosystem.

Remote-demo ingress, TLS termination, reverse proxies, load balancers, hosting products and public DNS are deliberately **not** selected by this ADR. The source does not prescribe an external deployment target, and `AMB-DEP-001` leaves that choice unresolved. If a concrete remote demonstration later requires edge infrastructure, it must be selected from the actual deployment constraints rather than precommitted here.

## Consequences

- the implementation follows the assignment's preferred ecosystem without pretending those preferences are functional requirements;
- common security, relational access, HTTP, UI, API-documentation and container concerns are reused rather than reimplemented;
- the relational stack reuses Spring's own JDBC infrastructure instead of introducing a second persistence lifecycle that the accepted workload does not need;
- Java 21 remains within the source's Java 17+ preference;
- the mandatory application topology is reproducible locally through Compose;
- no edge component becomes an architectural dependency before a remote deployment target exists.

The trade-off is a moderately broad dependency surface, controlled by requiring every non-trivial dependency to render a concrete durable service. A framework is not kept merely because it appeared in the initial baseline or assignment preference list.

## Alternatives not selected

### Custom UI/component stack or hand-written persistence/security infrastructure

Rejected because it adds code without increasing assignment value. Direct Spring JDBC is not hand-written infrastructure: Spring still owns datasource, transaction, resource, parameter-binding and exception-translation plumbing while project adapters own only explicit SQL and source-to-contract mapping.

### Hibernate/JPA as the relational baseline

Refined by [`ADR-007`](ADR-007-spring-jdbc-relational-adapters.md). Hibernate/JPA remains a valid mature option, but the accepted source-shaped workload currently does not need enough ORM lifecycle services to justify the extra behavioral layer.

### Kubernetes as the baseline runtime

Rejected because the assignment has no orchestration requirement and Compose is sufficient for the intended topology.

### Preselect a reverse proxy for a hypothetical remote demo

Deferred rather than selected. A reverse proxy may later be useful for TLS termination, routing or load balancing, but none of those concerns is part of the mandatory local baseline and the remote deployment target is unresolved.

## Requirement and constraint links

Primary links: `FR-AUTH-001`, `FR-HIST-001`, `FR-HIST-002`, `FR-RAG-001`, `NFR-REP-001`, `NFR-VER-001`, `NFR-SEC-001`, `CON-AI-001`, `CON-AI-002`.
