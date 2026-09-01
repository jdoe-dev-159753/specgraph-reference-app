# Customer Activity Analytics - SpecGraph Reference App

[![application-ci](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
[![demo-images](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml)
[![work-graph-guard](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml)

**A runnable reference application for specification-driven, AI-assisted software engineering.**

The current application lets a Customer Care operator inspect deterministic CARD, PAYMENT and CRYPTO activity plus source-shaped risk evidence. R2 replaces the synthetic activity adapter with PostgreSQL/Flyway persistence behind the same project-owned `CustomerActivityPort`; later rings add deterministic structured analysis/history, grounded retrieval, real multi-operator security and optional live-model differentiation.

Runtime stack: Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, React 19.2.8, TypeScript 7.0.2 and PostgreSQL 17 for R2.

## Run the current R2 from source

This is the shortest current path for exercising the PostgreSQL-backed R2 implementation before its separate OCI publication work is completed.

From the repository root, copy/paste:

```bash
docker compose up --build -d --wait r2 && H="$(hostname -I | awk '{print $1}')" && printf '\nR2: http://%s:8082/\n' "$H"
```

`docker compose` starts R2 and its PostgreSQL dependency, builds the current checkout, waits for health checks, and exposes the application on host port `8082`.

Open:

```text
http://<docker-host>:8082/
```

Use the deterministic seeded Customer ID:

```text
11111111-1111-1111-1111-111111111111
```

R2 should show the same application-owned customer/activity/risk contract as R1, now loaded from PostgreSQL through Spring JDBC. Monetary values remain exact decimal strings, source risk assessments preserve their own `assessmentId`, and activity/risk timestamps are converted from the explicitly configured source timezone.

The source timezone defaults to `UTC`. Override it for the R2 container when the source database wall-clock timestamps use another zone, for example:

```bash
SPECGRAPH_SOURCE_TIME_ZONE=Europe/Zurich docker compose up --build -d --wait r2
```

Stop the local R2 topology with:

```bash
docker compose down
```

`docker compose down` removes the PostgreSQL container as well, so the next launch starts again from the deterministic Flyway seed state.

## Published R0/R1 reviewer demo

R0 and R1 are already published as self-contained OCI checkpoints. Their remote Compose deployment does not require a source checkout, Maven, Node, or a local build.

Requires Docker Compose **2.34+**. The GHCR packages are private, so authenticate Docker once with a GitHub personal access token (classic) carrying `read:packages`:

```bash
docker login ghcr.io -u jdoe-dev-159753
```

Then launch the published checkpoints directly from GHCR:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait
```

For a one-line launch that prints browser addresses:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait && H="$(hostname -I | awk '{print $1}')" && printf '\nR0: http://%s:8080/\nR1: http://%s:8081/\n' "$H" "$H"
```

The published endpoints are:

```text
R0: http://<docker-host>:8080/
R1: http://<docker-host>:8081/
```

R0 is the intentionally hollow deployable shell. R1 is the first MANDATORY synthetic customer/activity/risk slice.

Stop the published deployment with:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo down
```

The remote Compose command is the durable reviewer contract. Issue #139 owns promotion of the accepted PostgreSQL-backed R2 checkpoint into this same OCI application after the remaining R2 scenario and operational prerequisites are green. Until then, use the source-checkout R2 command above rather than assuming `ghcr.io/...:r2` is published.

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

Those scripts intentionally remain the published R0/R1 operator path until #139 extends the canonical OCI checkpoint set to R2.

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

The local `compose.yaml` contains R0, R1 and R2. R2 depends on PostgreSQL health before application start. The published `compose.oci.yaml` currently contains the accepted R0/R1 checkpoint set; #139 owns adding published R2 without rewriting earlier checkpoint history.

## Architecture

The application is a modular monolith with hexagonal boundaries. Project-owned application/domain contracts remain inside the framework boundary; HTTP/UI and persistence/model/knowledge/history implementations are adapters behind those contracts.

The R2 customer-read path is:

```text
CustomerAnalysisHttpAdapter
        -> CustomerReviewUseCase
        -> CustomerReviewService
        -> CustomerActivityPort
        -> JdbcCustomerActivityAdapter
        -> PostgreSQL
```

Flyway is the schema/migration authority. Spring JDBC is the bounded R2 relational access baseline. Multi-query `CustomerSnapshot` reads use one PostgreSQL `REPEATABLE READ` snapshot so activities and risk evidence cannot be assembled from different committed database states.

Source `TIMESTAMP` columns are wall-clock values without timezone metadata. The application therefore does not guess from the host JVM or operating system. `specgraph.source-time-zone` is explicit configuration, exposed operationally as `SPECGRAPH_SOURCE_TIME_ZONE`, with deterministic fixture default `UTC`.

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
