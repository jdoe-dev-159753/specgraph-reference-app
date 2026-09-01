# Customer Activity Analytics - SpecGraph Reference App

[![application-ci](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
[![demo-images](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml)
[![work-graph-guard](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml)

**A runnable reference application for specification-driven, AI-assisted software engineering.**

The current application lets a Customer Care operator inspect deterministic CARD, PAYMENT and CRYPTO activity plus source-shaped risk evidence. R2 replaces the synthetic activity adapter with PostgreSQL/Flyway persistence behind the same project-owned `CustomerActivityPort`; later rings add deterministic structured analysis/history, grounded retrieval, real multi-operator security and optional live-model differentiation.

Runtime stack: Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, React 19.2.8, TypeScript 7.0.2 and PostgreSQL 17 for R2.

## Run the published R0/R1/R2 reviewer demo

The durable reviewer path is one remote Compose OCI command. It does not require a source checkout, Maven, Node or a local application build.

Requires Docker Compose **2.34+**. The GHCR packages are private, so authenticate Docker once with a GitHub personal access token (classic) carrying `read:packages`:

```bash
docker login ghcr.io -u jdoe-dev-159753
```

Then launch all accepted checkpoints directly from GHCR:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait
```

For a one-line launch that also prints the browser addresses:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait && H="$(hostname -I | awk '{print $1}')" && printf '\nR0: http://%s:8080/\nR1: http://%s:8081/\nR2: http://%s:8082/\n' "$H" "$H" "$H"
```

The published endpoints are:

```text
R0: http://<docker-host>:8080/
R1: http://<docker-host>:8081/
R2: http://<docker-host>:8082/
```

R0 is the intentionally hollow deployable shell. R1 is the first MANDATORY synthetic customer/activity/risk slice. R2 preserves that application-owned contract while loading customer activity and source risk evidence from PostgreSQL through Spring JDBC.

Use this deterministic seeded Customer ID in R1 or R2:

```text
11111111-1111-1111-1111-111111111111
```

R2 should expose CARD, PAYMENT and CRYPTO activity, exact decimal monetary strings, stable source `assessmentId` values and source timestamps converted from the explicit source timezone. The published reviewer checkpoint uses the deterministic fixture timezone `UTC`; the published topology starts PostgreSQL as an internal dependency and waits for it to become healthy before R2 starts.

Stop the published deployment with:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo down
```

`docker compose ... down` removes the PostgreSQL container as well, so the next R2 launch starts again from the deterministic Flyway seed state.

## Run the current checkout from source

The repository also keeps a source-build path for development and exact-head verification. From the repository root:

```bash
docker compose up --build -d --wait r2 && H="$(hostname -I | awk '{print $1}')" && printf '\nR2: http://%s:8082/\n' "$H"
```

The source-build path keeps `SPECGRAPH_SOURCE_TIME_ZONE` configurable. For a source database whose timezone-free wall-clock timestamps are Europe/Zurich values:

```bash
SPECGRAPH_SOURCE_TIME_ZONE=Europe/Zurich docker compose up --build -d --wait r2
```

Stop the local topology with:

```bash
docker compose down
```

## Fresh source checkout

A fresh host can authenticate through GitHub CLI and clone over HTTPS without creating an SSH keypair:

```bash
gh auth login --web --git-protocol https
gh repo clone jdoe-dev-159753/specgraph-reference-app
cd specgraph-reference-app
```

Plain HTTPS is also valid when Git credentials are already configured:

```bash
git clone https://github.com/jdoe-dev-159753/specgraph-reference-app.git
cd specgraph-reference-app
```

For R0/R1 local script operation, the existing project-owned launchers remain available:

```bash
./scripts/demo-up.sh
./scripts/demo-down.sh
```

## Runtime packaging

React is compiled at build time and embedded in the Spring Boot executable JAR. Each application checkpoint therefore runs as one Java 21 + embedded Tomcat container. R2 adds PostgreSQL as an external service while preserving the same HTTP/UI application boundary.

```text
build time
  React / TypeScript / Vite
            |
            v
      Spring Boot JAR
            |
            v
runtime  embedded Tomcat :8080
          /              \
       React UI          /api/*

R2 topology
  browser -> R2 application :8082 -> PostgreSQL :5432
```

The local `compose.yaml` and the published `compose.oci.yaml` both contain R0, R1 and R2. The published reviewer artifact preserves the accepted `demo/r0`, `demo/r1` and `demo/r2` source checkpoints side by side. R2 depends on PostgreSQL health before application start.

## Architecture

The application is a modular monolith with hexagonal boundaries. Project-owned application/domain contracts remain inside the framework boundary; HTTP/UI and persistence/model/knowledge/history implementations are adapters behind those contracts.

The R2 customer-read path is:

```text
CustomerReviewHttpAdapter
        -> CustomerReviewUseCase
        -> CustomerReviewService
        -> CustomerActivityPort
        -> JdbcCustomerActivityAdapter
        -> PostgreSQL
```

Flyway is the schema/migration authority. Spring JDBC is the bounded R2 relational access baseline. Multi-query `CustomerSnapshot` reads use one PostgreSQL `REPEATABLE READ` snapshot so activities and risk evidence cannot be assembled from different committed database states.

Source `TIMESTAMP` columns are wall-clock values without timezone metadata. The application therefore does not guess from the host JVM or operating system. `specgraph.source-time-zone` is explicit configuration, exposed operationally as `SPECGRAPH_SOURCE_TIME_ZONE` for source deployments, with deterministic fixture default `UTC`; the published reviewer checkpoint deliberately fixes that fixture semantics to UTC.

## Delivery rings

The delivery rings describe capability maturity, not calendar days. GitHub milestones `J1` through `J5` are the separate delivery timeboxes, so one day may contain more than one ring.

- **R0:** deployable hollow shell and stable seams.
- **R1:** MANDATORY synthetic customer/activity/risk review.
- **R2:** PostgreSQL/Flyway relational substitution behind the stable customer activity port.
- **R3:** MANDATORY deterministic structured analysis plus persistent reviewable analysis history.
- **R4:** MUST_HAVE real policy retrieval/grounding, multi-operator authentication/authorization and related trust boundaries.
- **R5:** hardening, reviewer/demo quality and NICE_TO_HAVE differentiation.

Authentication is deliberately not allowed to block the mandatory centre merely because its structural seam exists earlier.

## Canonical engineering evidence

Review the controlled documents in this order rather than inferring the system from implementation details alone:

- [Inception](docs/assignment/Inception/Inception.md) - problem framing, concentric delivery model and delivery strategy.
- [SRS](docs/assignment/SRS/SRS.md) - normative requirements and acceptance semantics.
- [SDD](docs/assignment/SDD/SDD.md) - architecture, modules, ports/adapters and delivery projection.
- [ADRs](docs/assignment/ADR/) - durable architecture and deployment decisions.
- [V&V](docs/assignment/VV/VV.md) - verification strategy, catalogue and evidence model.
- [OpenAPI](backend/src/main/resources/static/openapi.yaml) - deployed HTTP contract, also served at `/openapi.yaml`.

Machine-readable companions live beside the controlled human documents and support mechanically generated traceability.

## Engineering method

```text
problem evidence
  -> requirements / invariants
  -> acceptance criteria
  -> design / ADRs
  -> verification obligations
  -> implementation PRs
  -> deterministic gates + review
  -> retained executable evidence
```

AI can assist implementation and review, but deterministic claims remain mechanically testable. GitHub-native issue hierarchy, dependencies, PR ownership, lifecycle and typed Project metadata represent work state instead of prose surrogates.

## Confidentiality and reuse

The durable repository identity is generic. Proprietary assignment text and employer-specific naming are not required for the application architecture or reusable engineering method. Reusable mechanisms belong in the harness rather than being accumulated here as bespoke application infrastructure.
