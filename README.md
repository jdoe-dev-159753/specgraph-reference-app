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
![Platforms](https://img.shields.io/badge/runtime-linux%2Famd64%20%7C%20linux%2Farm64-informational)

**A runnable reference application for specification-driven, AI-assisted software engineering.**

The application lets a Customer Care operator search a customer, inspect deterministic CARD, PAYMENT and CRYPTO activity, and review source-shaped risk evidence. Later delivery rings add relational persistence, deterministic structured analysis, policy retrieval, analysis history and multi-operator authentication behind the same project-owned ports.

## Fast reviewer demo

The published R0/R1 checkpoint images and the deployment manifest are self-contained OCI artifacts. **Viewing the application does not require cloning the private source repository, creating an SSH key, installing Maven/Node, or building anything.**

Both checkpoints use the same runtime boundary: React is compiled ahead of time and embedded in the Spring Boot executable JAR; the running container is Java 21 + Spring Boot + embedded Tomcat. The published image references contain both `linux/amd64` and `linux/arm64`; Docker selects the appropriate runtime automatically.

### Canonical path: one remote Docker Compose deployment

Requires Docker Compose **2.34+**, which supports Compose applications published as OCI artifacts.

The GHCR packages are currently private, so authenticate Docker once. The value entered at the `Password` prompt is a **GitHub personal access token (classic) with `read:packages`**, not the GitHub account password:

```bash
docker login ghcr.io -u jdoe-dev-159753
```

Then, from any directory on the Docker host, start the complete published reviewer deployment directly from GHCR:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait
```

No local Compose file or Git checkout is involved. The OCI Compose artifact pins the exact published R0/R1 image digests.

For a copy/paste demo line that also prints browser-clickable addresses using the host's first advertised IP:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait && H="$(hostname -I | awk '{print $1}')" && printf '\nR0: http://%s:8080/\nR1: http://%s:8081/\n' "$H" "$H"
```

Then open both checkpoints side by side:

```text
R0: http://<docker-host>:8080/
R1: http://<docker-host>:8081/
```

R0 is the intentionally hollow deployable shell. R1 is the first MANDATORY customer/activity/risk slice. Use the deterministic seeded Customer ID in R1:

```text
11111111-1111-1111-1111-111111111111
```

Stop and remove the published deployment with:

```bash
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo down
```

This Compose command is the durable operator contract. When R2+ adds PostgreSQL or other mandatory services, the deployment artifact can grow while the command used by the reviewer stays the same.

If the OCI packages are deliberately made public later, GHCR allows anonymous pulls and the one-time `docker login` disappears; the Compose command remains unchanged. Package visibility is an explicit publication decision and is not changed implicitly by this repository.

### Direct checkpoint shortcut with `docker run`

R1 can still be launched directly when only one checkpoint is wanted:

```bash
docker run --rm --pull=always -p 8081:8080 ghcr.io/jdoe-dev-159753/specgraph-reference-app:r1
```

R0 likewise:

```bash
docker run --rm --pull=always -p 8080:8080 ghcr.io/jdoe-dev-159753/specgraph-reference-app:r0
```

`docker run` is useful for image/debug inspection, but Compose is the canonical system deployment abstraction because later rings are multi-service.

### Source checkout from a fresh host

Clone the repository only when source inspection, development, local build work or project-owned scripts are wanted. A fresh host does **not** need an SSH keypair: prefer GitHub CLI browser authentication over HTTPS.

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

With source checked out, the local configurable Compose/script path remains available:

```bash
./scripts/demo-up.sh
```

and:

```bash
./scripts/demo-down.sh
```

The preserved source branches `demo/r0` and `demo/r1` remain available for code inspection. Operational comparison uses prebuilt images and does not require rebuilding or switching the working tree.

## Runtime packaging and distribution

Each checkpoint is the same application shape, mapped to a different host port for side-by-side demonstration:

```text
build time on BUILDPLATFORM
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

OCI image manifest
  linux/amd64
  linux/arm64

side-by-side Compose deployment
  R0 container :8080 -> container :8080
  R1 container :8081 -> container :8080
```

[`ADR-005`](docs/assignment/ADR/ADR-005-prebuilt-demo-container-packaging.md) records the single-JAR checkpoint packaging. [`ADR-006`](docs/assignment/ADR/ADR-006-compose-oci-multi-platform-distribution.md) records Compose-as-OCI distribution and multi-platform Buildx publication.

Trusted `demo-images` automation builds the current packaging definition against the preserved `demo/r0` and `demo/r1` source checkpoints. It first proves both checkpoints natively, then publishes each checkpoint as one OCI manifest containing `linux/amd64` and `linux/arm64` variants. Moving convenience tags remain `r0` and `r1`; reproducible evidence uses compound immutable tags such as `r1-<checkpoint-sha>-pkg-<packaging-sha>` plus the OCI digest.

The workflow then publishes `compose.oci.yaml` itself with Docker Compose `publish --resolve-image-digests`. The moving deployment artifact is:

```text
ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo
```

An immutable Compose artifact tag also contains both checkpoint revisions and the packaging revision. After publication, CI removes its locally built checkpoint images and starts the system through the remote `oci://...:demo` artifact, then re-runs R1 Playwright. That gate proves the advertised reviewer deployment comes back from the registry rather than succeeding from the build cache.

The multi-platform Dockerfile deliberately runs Node/Vite and Maven on `BUILDPLATFORM`. Their outputs are architecture-neutral. Only the final Java 21 JRE layer varies by target platform, so ARM64 publication does not require emulating the complete build. This makes an ARM64 Docker host such as a Brume technically compatible with the same `r0`/`r1` references; resource sizing remains a separate deployment concern.

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

The implementation uses Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, React 19.2.8 and TypeScript 7.0.2. React/TypeScript/Vite are frontend build technologies; persistent execution is one Spring Boot executable JAR on embedded Tomcat per checkpoint.

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
