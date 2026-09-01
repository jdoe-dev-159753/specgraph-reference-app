# Customer Activity Analytics - SpecGraph Reference App

[![application-ci](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
[![demo-images](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml)
[![work-graph-guard](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml)
![Java 21](https://img.shields.io/badge/Java-21-informational)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-informational)
![Spring Modulith 2.1.1](https://img.shields.io/badge/Spring_Modulith-2.1.1-informational)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-informational)
![Node 24](https://img.shields.io/badge/Node-24_build--time-informational)
![React 19.2.8](https://img.shields.io/badge/React-19.2.8-informational)
![TypeScript 7.0.2](https://img.shields.io/badge/TypeScript-7.0.2-informational)
![Vite 8.1.0](https://img.shields.io/badge/Vite-8.1.0_build--time-informational)

**A runnable reference application for specification-driven, AI-assisted software engineering.**

The application lets a Customer Care operator inspect deterministic CARD, PAYMENT and CRYPTO activity plus source-shaped risk evidence. R2 replaces the synthetic activity adapter with PostgreSQL/Flyway persistence behind the same project-owned `CustomerActivityPort`. R3 adds deterministic structured analysis and persistent reviewable analysis history behind provider-neutral ports. R4 later adds real policy retrieval/grounding and multi-operator authentication/authorization.

## Published reviewer demo

The GHCR Compose tag `demo` is deliberately a **last-known-good publication**, not an alias for whatever happens to be on `main`. It advances only after `demo-images` has built the checkpoint images, resolved immutable digests, pulled the remote Compose artifact again and passed browser verification.

This matters when a publication run fails. Repository source may already describe a newer publication contract while `:demo` still resolves to the previous accepted artifact. Missing checkpoint containers then mean publication has not completed, not that the advertised port is secretly handled elsewhere.

Requires Docker Compose **2.34+**. The GHCR packages are private, so authenticate Docker once with a GitHub personal access token (classic) carrying `read:packages`:

```bash
docker login ghcr.io -u jdoe-dev-159753
```

Launch the last successfully published checkpoint set directly from GHCR:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait
```

Inspect what the published artifact actually contains:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo config --services
```

The complete J2 publication contract contains R0, R1, PostgreSQL-backed R2 and deterministic analysis/history R3 side by side:

```text
R0: http://<docker-host>:8080/
R1: http://<docker-host>:8081/
R2: http://<docker-host>:8082/
R3: http://<docker-host>:8083/
```

Until the J2 publication issues are completed by a green remote-pull proof, the `demo` tag may legitimately remain on an earlier last-known-good set. The workflow does not overwrite a working reviewer artifact merely because source code was merged.

R0 is the intentionally hollow deployable shell. R1 is the first MANDATORY synthetic customer/activity/risk slice. R2 preserves that application-owned contract while loading customer activity and source risk evidence from PostgreSQL through Spring JDBC. R3 keeps the same source evidence and adds deterministic structured analysis plus persisted reviewable history.

Stop the published deployment with:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo down
```

The reviewer topology uses an ephemeral PostgreSQL container for deterministic fixture state. Removing the deployment resets the fixture database for the next launch.

## Run the current checkout from source

The source path is independent of GHCR publication and is authoritative for an unmerged candidate.

Run R3:

```bash
docker compose up --build -d --wait r3
```

Then open:

```text
R3: http://localhost:8083/
```

Run the accepted frozen R2 source checkpoint instead of rebuilding the current R3 checkout under the R2 service name:

```bash
git worktree add --detach .checkpoints/r2 demo/r2
R2_SOURCE_ROOT=.checkpoints/r2 docker compose up --build -d --wait r2
```

Then open:

```text
R2: http://localhost:8082/
```

The source-build path keeps `SPECGRAPH_SOURCE_TIME_ZONE` configurable. For timezone-free source timestamps representing Europe/Zurich wall-clock values:

```bash
SPECGRAPH_SOURCE_TIME_ZONE=Europe/Zurich docker compose up --build -d --wait r3
```

A deterministic clean reset requires no hand-edited SQL because the source Compose topology does not persist PostgreSQL in a named volume:

```bash
docker compose down --remove-orphans
docker compose up --build -d --wait r3
```

## Deterministic J2 scenario catalogue

The PostgreSQL fixture is a small set of coherent reviewer stories, not random transaction noise. Risk assessments are explicit synthetic source evidence; the application never turns these scenarios into a claim that a customer committed wrongdoing.

| Customer ID | Reviewer story |
| --- | --- |
| `11111111-1111-1111-1111-111111111111` | R1-compatible CARD + PAYMENT + CRYPTO story with persisted source risk evidence |
| `22222222-2222-2222-2222-222222222222` | stable local CHF baseline with no risk assessments |
| `33333333-3333-3333-3333-333333333333` | conventional baseline followed by growing cross-border payments and new crypto activity |
| `44444444-4444-4444-4444-444444444444` | mixed anomaly story with repeated card declines, high-value cross-border movement and crypto evidence |

R3 uses the same source evidence and adds a deterministic structured analysis result (`LOW | MEDIUM | HIGH`), policy-evidence provenance, operator attribution and persistent history. A reload can therefore review the previously persisted analysis instead of manufacturing a second transient answer.

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

## Runtime packaging

React is compiled at build time and embedded in the Spring Boot executable JAR. Maven, Node and Vite are build tools, not reviewer runtime services. Each application checkpoint therefore runs as one Java 21 + embedded Tomcat container.

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

J2 reviewer topology
  R0 :8080
  R1 :8081
  R2 :8082 ----\
                +--> PostgreSQL :5432 (private Compose network)
  R3 :8083 ----/
```

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

The R3 analysis path extends the same architecture:

```text
AnalysisHttpAdapter
        -> AnalysisUseCase
        -> AnalysisService
        -> CustomerActivityPort
        -> PolicyKnowledgePort
        -> AnalysisModelPort
        -> structured-result validation
        -> AnalysisHistoryPort
        -> PostgreSQL
```

Flyway is the schema/migration authority. Spring JDBC is the bounded relational access baseline. Multi-query `CustomerSnapshot` reads use one PostgreSQL `REPEATABLE READ` snapshot so activities and risk evidence cannot be assembled from different committed database states.

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
