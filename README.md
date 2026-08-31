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

The published R0/R1 checkpoint images are self-contained. **Viewing the application does not require cloning the private source repository, creating an SSH key, installing Maven/Node, or building anything.**

Both checkpoints use the same packaging boundary: React is compiled ahead of time and embedded in the Spring Boot executable JAR; the running container is Java 21 + Spring Boot + embedded Tomcat.

### Fastest path: run R1 directly from Docker

The GHCR package is currently private, so authenticate Docker once. The value entered at the `Password` prompt is a **GitHub personal access token (classic) with `read:packages`**, not the GitHub account password:

```bash
docker login ghcr.io -u jdoe-dev-159753
```

Then launch R1 directly. No Git checkout is involved:

```bash
docker run --rm --pull=always -p 8081:8080 ghcr.io/jdoe-dev-159753/specgraph-reference-app:r1
```

Open `http://localhost:8081/` when the browser runs on the Docker host, or `http://<docker-host>:8081/` from another machine.

Use the deterministic seeded Customer ID:

```text
11111111-1111-1111-1111-111111111111
```

The R1 UI exposes CARD, PAYMENT and CRYPTO activity plus associated risk evidence. Searching an unknown UUID exercises the explicit not-found path. Stop the foreground container with `Ctrl+C`.

### Run R0 directly

R0 is the intentionally hollow pre-business checkpoint:

```bash
docker run --rm --pull=always -p 8080:8080 ghcr.io/jdoe-dev-159753/specgraph-reference-app:r0
```

Open `http://localhost:8080/` locally or `http://<docker-host>:8080/` remotely.

### Run R0 and R1 side by side without cloning the repository

```bash
docker run -d --rm --pull=always --name specgraph-r0 -p 8080:8080 ghcr.io/jdoe-dev-159753/specgraph-reference-app:r0
docker run -d --rm --pull=always --name specgraph-r1 -p 8081:8080 ghcr.io/jdoe-dev-159753/specgraph-reference-app:r1
```

Then open:

```text
R0: http://<docker-host>:8080/
R1: http://<docker-host>:8081/
```

Stop both with:

```bash
docker rm -f specgraph-r0 specgraph-r1
```

This is the shortest reviewer path today: one GHCR authentication on a fresh Docker host, then plain Docker commands. If the GHCR package is deliberately made public later, public GHCR images support anonymous pulls and the `docker login` step disappears; the `docker run` commands stay unchanged. Making a package public is an explicit publication decision and is not required for the current private-source workflow.

### Source checkout from a fresh host

Clone the repository only when source inspection, development, Compose orchestration, or the project-owned demo scripts are wanted. A fresh host does **not** need an SSH keypair: prefer GitHub CLI browser authentication over HTTPS.

```bash
gh auth login --web --git-protocol https
gh repo clone jdoe-dev-159753/specgraph-reference-app
cd specgraph-reference-app
```

The plain HTTPS clone URL is also:

```bash
git clone https://github.com/jdoe-dev-159753/specgraph-reference-app.git
cd specgraph-reference-app
```

Because the repository is private, plain `git clone` still requires a GitHub credential; `gh auth login --web` is the preferred route when avoiding SSH key management.

Once the source is checked out, the reproducible side-by-side Compose path remains:

```bash
./scripts/demo-up.sh
```

The script pulls both prebuilt images, starts them detached without building, waits for both healthchecks, then prints browser-usable R0/R1 URLs. If Docker has not already been authenticated to the private package, run the `docker login ghcr.io -u jdoe-dev-159753` command above once.

If the browser should use an address other than the host's fully-qualified hostname, configure it once:

```bash
cp .env.demo.example .env.demo
$EDITOR .env.demo
```

`.env.demo` is ignored by Git. Physical lab/VPS addressing remains deployment configuration rather than application source.

Stop the Compose path with:

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

After publication, the workflow removes its local checkpoint images and invokes the same `./scripts/demo-up.sh` command used by a reviewer with a source checkout. That gate proves the side-by-side Compose demonstration is actually pulled back from GHCR rather than accidentally succeeding from the runner's local image cache. The even shorter direct `docker run` path uses the same published `r0` and `r1` images without making source checkout a runtime dependency.

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
