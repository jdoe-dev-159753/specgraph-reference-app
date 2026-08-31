# ADR-005 — Package reviewer checkpoints as one prebuilt Spring Boot application image

**Decision date:** 2026-08-31  
**Decision owner:** J1 executable demonstration and deployment packaging  
**Normative inputs:** `NFR-REP-001`, `NFR-VER-001`, `VFY-DELIVERY-001`, `ADR-001`, `ADR-004`

## Context

ADR-004 deliberately chose Java/Spring/React and Docker Compose while deferring deployment packaging until a concrete reviewer/demo target existed. J1 initially proved the topology by running the backend from a Maven image and the frontend from a Node/Vite development image. A first packaging draft then replaced those with a JRE backend image plus a separate Caddy/static-frontend image.

That second runtime is unnecessary. React is required only while producing browser assets. Spring Boot already includes embedded Tomcat and can serve classpath static resources alongside Spring MVC endpoints. Keeping an additional HTTP process merely to serve the React build increases runtime surface without increasing assignment value.

JSP/Tomcat remain viable Java web technologies, but Spring Boot documents JSP limitations for executable JAR deployments. Replacing the accepted React UI with JSP would also discard an explicit assignment technology preference. A server-side template engine such as Thymeleaf would be a reasonable alternative in a product with no React preference, but is not selected here.

The demonstration has a second requirement beyond packaging: R0 and R1 should be visibly comparable. They are not competing REST API versions inside one runtime. They are complete executable checkpoints of the same product at successive delivery rings. Running both checkpoint images simultaneously is therefore a more faithful demonstration than hiding them behind `/v0` and `/v1` routes or repeatedly stopping one to start the other.

## Decision

### One packaged application per checkpoint

Use one multi-stage Docker build:

1. Node/Vite builds the React/TypeScript frontend;
2. the generated `dist` assets are copied into `src/main/resources/static` of the Spring Boot build stage;
3. Maven packages the backend plus those static assets as one executable Spring Boot JAR;
4. the runtime image contains only a Java 21 JRE and that JAR.

The runtime entrypoint is:

```text
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Embedded Tomcat serves both surfaces from the same origin:

```text
/          -> built React static assets
/api/*     -> Spring MVC application endpoints
/actuator  -> runtime health/evidence endpoints
```

There is therefore no Node process, Vite development server, Caddy/nginx reverse proxy, or separately deployed frontend service in the J1 persistent runtime.

### Side-by-side checkpoint topology

The reviewer demo runs two instances of that same package shape:

```text
R0 image -> container Tomcat :8080 -> host :8080
R1 image -> container Tomcat :8080 -> host :8081
```

The differing host ports are a demonstration concern only. They avoid an additional routing/proxy tier and let a reviewer keep both checkpoints open in separate browser tabs. R0 remains the hollow deployable shell; R1 remains the first MANDATORY customer/activity/risk acceptance slice.

A path-based form such as `/r0` and `/r1` was considered but not selected. It would require another HTTP routing layer or application context-path changes solely to make the URLs prettier, while providing no engineering value over two explicit port mappings.

### Checkpoint images and provenance

Trusted automation builds the current packaging definition against preserved `demo/r0` and `demo/r1` source checkpoints, verifies both packaged deployments simultaneously, and publishes checkpoint images to GitHub Container Registry.

The operator-facing convenience tags `r0` and `r1` intentionally move when a newly verified image for that checkpoint is published. They are not evidence identifiers.

Immutable evidence identity includes **both** revisions which determine the packaged artifact:

```text
r0-<checkpoint-sha>-pkg-<packaging-sha>
r1-<checkpoint-sha>-pkg-<packaging-sha>
```

The checkpoint SHA identifies the preserved application source; the packaging SHA identifies the `main` revision that supplied the Dockerfile, Compose/runtime definition and publication workflow. The publication step also records the resulting OCI digest in the GitHub Actions step summary. A packaging change can therefore never silently overwrite the immutable identifier of an older tested image even when the R0/R1 source checkpoint itself does not move.

The source checkpoint branches remain available for code inspection. Operational selection uses the moving `r0`/`r1` image tags; retained verification evidence uses the compound immutable tag and OCI digest.

### Operator entry path

A host-local, untracked `.env.demo` may define the browser-reachable host and ports once. The nominal reviewer command is then:

```text
./scripts/demo-up.sh
```

It pulls both moving checkpoint tags, starts both containers detached without building, waits for both healthchecks, and prints two browser-usable URLs only after both instances are ready:

```text
R0 ready: http://<demo-host>:8080/
R1 ready: http://<demo-host>:8081/
```

Individual `r0` and `r1` modes remain available for diagnostics, but the default demonstration deliberately presents both checkpoints at once.

The trusted publication workflow removes its local checkpoint images after pushing, then runs this same default command and re-verifies R1 with Playwright. This proves the operator path performs a real GHCR pull rather than succeeding only because the runner retained locally built images.

## Consequences

- reviewer startup becomes two image pulls plus two small JVM/container starts;
- R0 and R1 can remain open in adjacent browser tabs during the explanation of concentric delivery;
- the same application package works for local, lab-host and VPS-style operation;
- React remains visible in the engineering solution without becoming an extra runtime process;
- backend and browser assets are versioned atomically in one image/JAR per checkpoint;
- same-origin browser/API traffic removes CORS and reverse-proxy configuration from J1;
- runtime contains only the capability required to run the packaged application;
- moving `r0`/`r1` tags remain convenient for the demo while compound tags plus OCI digests preserve exact provenance;
- R0 remains a demonstrable hollow checkpoint and R1 remains the first acceptance-bearing mandatory slice;
- GHCR requires one-time authentication for private packages;
- a lab-local OCI cache/registry may later reduce pull latency, but it is not an application prerequisite.

## Alternatives not selected

### Separate static frontend/reverse-proxy container

Rejected after review. It is conventional for independently deployed frontends, but this application does not require independent frontend scaling or deployment. The extra process/network boundary adds no J1 value.

### Route R0/R1 behind one hostname with path prefixes

Rejected for J1. `/r0` and `/r1` look tidy but require a routing tier or context-path rewriting solely for presentation. Two host ports preserve independent immutable application instances with less infrastructure.

### JSP + external or executable-WAR Tomcat deployment

Rejected. Tomcat and JSP still exist, but Spring Boot's executable-JAR path has known JSP limitations, and the assignment explicitly prefers React.

### Thymeleaf/server-rendered Spring MVC UI

Technically viable and simpler for a Java-only application, but not selected because React is an explicit preferred technology and the existing React UI is already accepted and browser-tested.

### Source-only immutable tags

Rejected after review. A tag containing only the R0/R1 source SHA can be overwritten by a different image when packaging changes while the preserved source checkpoint remains unchanged. Exact evidence therefore includes both source and packaging revisions, with the registry digest retained as the final content identity.

### Build on the demo host

Rejected because it turns a deterministic demo into a live build demonstration and adds avoidable latency/failure modes.

### Make a local lab registry mandatory for J1

Deferred. GHCR already provides authenticated package distribution from trusted Actions. A local registry/cache is shared-lab infrastructure and should be justified by measured benefit.

## Requirement and verification links

Primary links: `NFR-REP-001`, `NFR-VER-001`, `VFY-DELIVERY-001`. The packaged R1 browser path remains verified by `VFY-CUSTOMER-READ-001`; R0 verification proves deployment/readiness only and does not claim R1 business acceptance.
