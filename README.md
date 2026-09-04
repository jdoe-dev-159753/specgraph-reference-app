# Customer Activity Analytics

[![application-ci](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
[![demo-images](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml)
[![work-graph-guard](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/work-graph-guard.yml)

Customer Activity Analytics is a runnable customer-care application for reviewing customer activity, risk evidence, applicable policy and retained analysis history. It demonstrates how a specification-driven delivery can combine deterministic controls, statistical detectors, local retrieval and optional language-model adapters without letting generated text replace source evidence.

The demo data is synthetic. Detector scores are reviewer signals for this bounded scenario; they are not calibrated production AML probabilities and do not assert wrongdoing.

## Start here

| What | Link |
| --- | --- |
| R4 baseline and Bayesian demo | [Run the R4 demo](#docker-quickstart) |
| R4 implementation guide | [Open the reviewer guide](docs/reviewer/r4-gallery.md) |
| R5 / J5 final product candidate | [Track R5 delivery](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/398) |
| J5 immutable submission | [Track the final release](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/127) |

R4 is the currently runnable product foundation. R5 is a capability-maturity target: remaining evidence and optional classical-ML work must be completed or explicitly excluded before it can be called delivered.

## Docker quickstart

Prerequisites: Docker with Compose 2.34+ and read access to the repository's private GHCR packages.

```bash
docker login ghcr.io -u jdoe-dev-159753
docker compose -p customer-activity-demo \
  -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo \
  up -d --wait
```

The `:demo` tag advances only after remote-pull and browser verification; a failed publication leaves the previous accepted demo intact.

Open the two R4 configurations after startup:

- [R4 baseline — http://localhost:8084/](http://localhost:8084/)
- [R4 Bayesian — http://localhost:8085/](http://localhost:8085/)

Replace `localhost` with the Docker host name or IP when Docker runs remotely. On the private demonstration host the usual addresses are `http://watch-infra-01:8084/` and `http://watch-infra-01:8085/`.

Sign in with either repository-owned demo account:

| Operator | Password |
| --- | --- |
| `operator-alpha` | `alpha-demo-2026` |
| `operator-beta` | `beta-demo-2026` |

Search for customer `44444444-4444-4444-4444-444444444444`, run an analysis, inspect its evidence and reload the page to confirm retained history. Use separate browser profiles when comparing both variants so their sessions remain isolated.

To confirm the Bayesian variant, inspect the browser network response from `POST /api/customers/{customerId}/analyses`: `detectorProvenance` identifies `beta-binomial-review-elevation-v1`, while the baseline has no detector provenance.

Stop and reset the demo with Docker Compose:

```bash
docker compose -p customer-activity-demo \
  -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo \
  down -v
```

If GHCR access is unavailable, the [R4 reviewer guide](docs/reviewer/r4-gallery.md) gives the Docker-only source-build commands for the same baseline and Bayesian comparison.

## What the reviewer is seeing

The application keeps four authorities distinct:

1. persisted CARD, PAYMENT and CRYPTO activity plus source risk assessments;
2. optional detector evidence, with no-op baseline and Bayesian configuration demonstrated side by side;
3. relevant policy evidence retrieved from PostgreSQL/pgvector with local MiniLM embeddings;
4. structured advisory synthesis, selected explicitly as deterministic, local LM Studio or external OpenAI.

Only bounded, citable evidence crosses the model boundary. The deterministic backend is the no-credential default; provider credentials configure an adapter but never select it implicitly.

Local LM Studio is an explicit opt-in through `SPECGRAPH_ANALYSIS_BACKEND=local`; its base URL must use a loopback, private, link-local or ULA IP literal rather than a hostname. From a Linux VPS, use the Windows LAN IP because loopback is valid only when the application and LM Studio share a host. The [R4 reviewer guide](docs/reviewer/r4-gallery.md) owns the Docker-only launch details.

![Hexagonal architecture, ports and adapters](docs/assignment/SDD/diagrams/hexagonal-architecture.svg)

The runtime is one modular monolith with hexagonal boundaries. HTTP/UI, relational persistence, detectors, retrieval and model providers remain adapters behind application-owned ports. R5 extends evidence and capability maturity without creating another runtime stack.

## Evidence

- [R4 gallery and copy/paste Docker commands](docs/reviewer/r4-gallery.md)
- [Authentic screenshot provenance](docs/reviewer/screenshot-manifest.md)
- [Architecture figures and controlled sources](docs/reviewer/architecture-figures.md)
- [Application CI runs](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
- [Published-image and remote-pull proof](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/demo-images.yml)
- [Configuration-sensitive R4 browser evidence](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/r4-gallery-ci.yml)

## Engineering documents

- [SRS — requirements and acceptance semantics](docs/assignment/SRS/SRS.md)
- [SDD — architecture, modules and trust boundaries](docs/assignment/SDD/SDD.md)
- [Architecture decisions](docs/assignment/ADR/), including the [public product identity and final-freeze boundary](docs/assignment/ADR/ADR-008-customer-activity-analytics-identity.md)
- [V&V — verification strategy and evidence model](docs/assignment/VV/VV.md)
- [OpenAPI — deployed HTTP contract](backend/src/main/resources/static/openapi.yaml)
- [Proprietary evaluation license](LICENSE)

PlantUML sources beside the rendered SVGs are authoritative. GitHub issues, pull requests, checks and exact-SHA artifacts own delivery state; this README stays a concise entry point rather than duplicating those records.
