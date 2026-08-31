# Customer Activity Analytics — SpecGraph Reference App

[![application-ci](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
[![demo-images](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml)
[![work-graph-guard](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml)
![Java 21](https://img.shields.io/badge/Java-21-informational)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-informational)
![Spring Modulith 2.1.1](https://img.shields.io/badge/Spring_Modulith-2.1.1-informational)
![Node 24](https://img.shields.io/badge/Node-24_build--time-informational)
![React 19.2.8](https://img.shields.io/badge/React-19.2.8-informational)
![TypeScript 7.0.2](https://img.shields.io/badge/TypeScript-7.0.2-informational)
![Vite 8.1.0](https://img.shields.io/badge/Vite-8.1.0_build--time-informational)

**A runnable reference application for specification-driven, AI-assisted software engineering.**

The application lets a Customer Care operator search a customer, inspect deterministic CARD, PAYMENT and CRYPTO activity, and review source-shaped risk evidence. Later delivery rings add relational persistence, deterministic structured analysis, policy retrieval, analysis history and multi-operator authentication behind the same project-owned ports.

## Fast reviewer demo

The normal reviewer path runs **R0 and R1 simultaneously** from prebuilt checkpoint images so the concentric delivery model can be demonstrated rather than merely described.

Both checkpoints use the same packaging boundary: React is compiled ahead of time and embedded in the Spring Boot executable JAR; the running container is Java 21 + Spring Boot + embedded Tomcat. No Maven, Node, Vite or reverse proxy runs while somebody is watching.

### One-time setup on a Docker host

```bash
git clone git@github.com:jdoe-dev-159753/specgraph-reference-app.git
cd specgraph-reference-app
```

Authenticate Docker once to the private GHCR package:

```bash
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u jdoe-dev-159753 --password-stdin
```

If the browser should use an address other than the host's own fully-qualified hostname, configure it once:

```bash
cp .env.demo.example .env.demo
$EDITOR .env.demo
```

`.env.demo` is ignored by Git. Physical lab/VPS addressing remains deployment configuration rather than application source.

### Day-of-demo: both checkpoints side by side

```bash
./scripts/demo-up.sh
```

The command pulls both prebuilt images, starts them detached without building, waits for both healthchecks, then returns only after printing two browser-usable URLs:

```text
R0 ready: http://demo-host:8080/
R1 ready: http://demo-host:8081/
```

Open both in separate tabs. R0 is intentionally hollow and proves the deployable shell existed before business acceptance. R1 is the first MANDATORY customer/activity/risk slice.

For R1, use the deterministic seeded Customer ID:

```text
11111111-1111-1111-1111-111111111111
```

The R1 UI exposes CARD, PAYMENT and CRYPTO activity plus associated risk evidence. Searching an unknown UUID exercises the explicit not-found path.

Stop both checkpoints with:

```bash
./scripts/demo-down.sh
```

Individual diagnostic modes remain available:

```bash
./scripts/demo-up.sh r0
./scripts/demo-up.sh r1
./scripts/demo-down.sh r0
./scripts/demo-down.sh r1
```

The preserved source branches `demo/r0` and `demo/r1` remain available for code inspection. Operational comparison uses prebuilt images and does not require rebuilding or switching the working tree.

## Runtime packaging

Each checkpoint is the same deployment shape, mapped to a different host port for side-by-side demonstration:

```text
build time
  Node/Vite -> React static assets
                  |
                  v
            Maven/Spring Boot
                  |
                  v
runtime      executable JAR
                  |
          embedded Tomcat :8080
             /          /api/*
          React UI    Spring MVC

side-by-side demo host
  R0 container :8080 -> container :8080
  R1 container :8081 -> container :8080
```

This keeps the assignment's preferred React technology without inventing an independently deployed frontend. [`ADR-005`](docs/assignment/ADR/ADR-005-prebuilt-demo-container-packaging.md) records the packaging and checkpoint-comparison decision.

Trusted `demo-images` automation builds the same packaging definition against the preserved `demo/r0` and `demo/r1` source checkpoints, proves both start simultaneously, proves R1 with Playwright, then publishes moving GHCR convenience tags `r0` and `r1`. Reproducible evidence uses compound immutable tags such as `r1-<checkpoint-sha>-pkg-<packaging-sha>`; the workflow summary records checkpoint revision, packaging revision and OCI digest for the exact image tested and published.

After publication, the workflow removes its local checkpoint images and invokes the same `./scripts/demo-up.sh` command used by a reviewer. That final gate proves the side-by-side demonstration is actually pulled back from GHCR rather than accidentally succeeding from the runner's local image cache.

## Canonical engineering evidence

A reviewer should follow these controlled authorities rather than infer design from code alone:

- [Inception](docs/assignment/Inception/Inception.md) — problem framing, delivery rings and functional use-case orientation
- [SRS](docs/assignment/SRS/SRS.md) — normative requirements and acceptance semantics
- [SDD](docs/assignment/SDD/SDD.md) — architecture, modules, ports/adapters and delivery projection
- [ADRs](docs/assignment/ADR/) — durable architectural and deployment decisions
- [V&V](docs/assignment/VV/VV.md) — verification strategy and evidence model

Machine-readable companions live beside those documents and remain the authority for mechanically generated traceability views.

## Architecture

The application is a modular monolith with hexagonal boundaries. Project-owned application/domain contracts sit inside the framework boundary; inbound HTTP/UI adapters and outbound activity, persistence, knowledge, model and history adapters remain replaceable behind stable ports.

The implementation uses Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, React 19.2.8 and TypeScript 7.0.2. React/TypeScript/Vite are frontend build technologies; persistent J1 execution is one Spring Boot executable JAR on embedded Tomcat.

The key R1 request path is intentionally:

```text
CustomerAnalysisHttpAdapter
        -> CustomerReviewUseCase
        -> CustomerReviewService
        -> CustomerActivityPort
        -> SyntheticActivityAdapter
```

An architecture test ratchets this boundary so the HTTP adapter cannot silently resume calling the outbound port directly. Spring Modulith independently verifies modular-monolith boundaries in executable tests.

## Delivery rings

- **R0:** deployable hollow shell and stable seams;
- **R1:** MANDATORY synthetic customer/activity/risk review;
- **R2:** relational adapter substitution behind the existing activity port;
- **R3:** MANDATORY deterministic structured analysis;
- **R4:** MUST_HAVE authentication/security, policy retrieval, persistence/history and related evidence;
- **R5:** hardening, demonstration quality and NICE_TO_HAVE differentiation.

Authentication is deliberately not allowed to block the mandatory centre merely because its structural seam exists earlier.

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

AI can assist implementation and review, but deterministic claims stay mechanically testable. GitHub-native issues, PR ownership, dependencies, lifecycle and Project metadata represent work state; prose documents do not duplicate those states.

## Confidentiality and reuse

The durable repository identity is generic. Proprietary assignment text and employer-specific naming are not required for the application architecture or the reusable engineering method. Mechanisms that are useful independently of this domain belong in the harness rather than being accumulated here as bespoke infrastructure.
