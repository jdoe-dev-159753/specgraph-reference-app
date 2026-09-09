# Customer Activity Analytics

[![application-ci](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-informational)](backend/pom.xml)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-informational)](backend/pom.xml)
[![Spring AI 2.0.1](https://img.shields.io/badge/Spring_AI-2.0.1-informational)](backend/pom.xml)
[![React 19](https://img.shields.io/badge/React-19-informational)](frontend/package.json)
[![PostgreSQL 17 + pgvector](https://img.shields.io/badge/PostgreSQL_17-pgvector_0.8.6-informational)](compose.r4.yaml)

Customer Activity Analytics is a runnable synthetic customer-review application built to demonstrate specification-driven software delivery and provider-neutral AI integration.

It keeps source activity, source risk, derived detector signals, retrieved policy evidence and generated advisory text as separate evidence classes. Language-model output never becomes source truth merely because a model produced it.

> **Scope:** this is a technical demonstrator using synthetic data. Detector scores and generated analyses are reviewer signals, not calibrated production AML decisions and not assertions of wrongdoing.

## What it demonstrates

The application combines:

- a Java 21 / Spring Boot modular monolith with Spring Modulith architecture checks;
- explicit hexagonal ports for persistence, retrieval, detector and analysis-model boundaries;
- PostgreSQL and pgvector with Flyway-owned schema evolution;
- authenticated multi-operator review flows and retained analysis provenance;
- interchangeable Stage-1 detectors including Bayesian, fuzzy and packaged Random Forest mechanisms;
- local MiniLM embeddings and pgvector policy retrieval;
- deterministic, local LM Studio and optional OpenAI Stage-3 analysis adapters behind one application-owned model port;
- React + TypeScript reviewer UI and browser-level Playwright evidence;
- controlled requirements, architecture decisions, design and V&V artifacts tied to executable evidence.

The AI path is deliberately staged:

```text
source activity + source risk
          │
          ▼
Stage 1: derived detector evidence
          │
          ▼
Stage 2: policy retrieval / grounding
          │
          ▼
bounded application-owned evidence envelope
          │
          ▼
Stage 3: advisory synthesis
          │
          ▼
validation + retained provenance/history
```

Changing a detector, retrieval implementation or model provider does not redefine the application contracts around it.

## Run the deterministic demo

The deterministic R4 configuration requires Docker Compose but no external model credential.

```bash
docker compose -f compose.r4.yaml up -d --build
```

Open <http://localhost:8084/> and sign in with either synthetic demo operator:

| Operator | Password |
| --- | --- |
| `operator-alpha` | `alpha-demo-2026` |
| `operator-beta` | `beta-demo-2026` |

A useful review case is customer:

```text
44444444-4444-4444-4444-444444444444
```

Run an analysis, inspect detector evidence and pgvector grounding, then reload the page to verify retained history.

Stop and remove the disposable demo state with:

```bash
docker compose -f compose.r4.yaml down -v
```

The first R4 startup may populate the local embedding-model cache. The richer local-model R5 configuration is documented separately in [`docs/reviewer/r5-runtime.md`](docs/reviewer/r5-runtime.md); it is not required for the default portfolio path.

## Verify from source

Backend verification, including PostgreSQL/Testcontainers integration tests and JaCoCo generation:

```bash
mvn -f backend/pom.xml verify
```

Frontend type-check and production build:

```bash
cd frontend
npm ci
npm run build
```

The retained [`r4-acceptance-ci`](.github/workflows/r4-acceptance-ci.yml) workflow exercises the complete authenticated browser flow, grounding, history, determinism and degradation behavior against an exact source revision.

Repository-wide coverage is being consolidated from the native language reports rather than represented by a Java-only percentage. Coverage is treated as a regression signal, not as proof of correctness.

## Architecture

![Hexagonal architecture, ports and adapters](docs/assignment/SDD/diagrams/hexagonal-architecture.svg)

The backend keeps module ownership explicit and prevents provider/framework types from leaking into application contracts. Spring owns composition and lifecycle; application-owned ports own substitution semantics.

Key boundaries include:

- `CustomerActivityPort` for customer activity and source-risk reads;
- `RiskSignalDetectorPort` for interchangeable derived Stage-1 evidence;
- `PolicyKnowledgePort` for grounded policy retrieval;
- `AnalysisModelPort` for Stage-3 synthesis;
- `AnalysisHistoryPort` for retained completed analyses and provenance.

The frontend consumes the same bounded HTTP contracts and exposes evidence provenance rather than collapsing the pipeline into one opaque “AI result”.

## Review evidence

- [SRS: requirements and acceptance semantics](docs/assignment/SRS/SRS.md)
- [SDD: architecture, modules and trust boundaries](docs/assignment/SDD/SDD.md)
- [Architecture decisions](docs/assignment/ADR/)
- [V&V strategy and evidence model](docs/assignment/VV/VV.md)
- [OpenAPI contract](backend/src/main/resources/static/openapi.yaml)
- [Architecture figures](docs/reviewer/architecture-figures.md)
- [Authentic screenshot provenance](docs/reviewer/screenshot-manifest.md)
- [R4 fallback gallery](docs/reviewer/r4-gallery.md)
- [R5 local-model reviewer guide](docs/reviewer/r5-runtime.md)
- [Current presentation](docs/presentation/output/SpecGraph_presentation_working_v0.8.pptx)

The original assignment state remains preserved by the immutable [`submission-v1`](https://github.com/jdoe-dev-159753/specgraph-reference-app/tree/submission-v1) tag. Portfolio cleanup happens after that boundary so delivery evidence remains auditable without forcing every delivery-era mechanism to stay active forever.

## Repository map

```text
backend/          Spring Boot application, ports, adapters and tests
frontend/         React / TypeScript reviewer UI
e2e/              Playwright acceptance scenarios
docs/assignment/  controlled requirements, design, ADR and V&V artifacts
docs/reviewer/    compact reviewer evidence and runtime notes
docs/presentation current presentation source and output
docker/           application and test container recipes
scripts/          retained build, verification and runtime tooling
```

The portfolio edition is intentionally simplifying the last three surfaces: redundant delivery workflows, compatibility overlays and orchestration wrappers are removed when the frozen submission history already preserves their evidence.

## License

See [LICENSE](LICENSE). The repository is published for evaluation and review under its stated proprietary evaluation terms.
