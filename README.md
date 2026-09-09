# Customer Activity Analytics

[![application-ci](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/application-ci.yml)
[![R5 release](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/r5-release.yml/badge.svg?branch=main)](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/workflows/r5-release.yml)

Customer Activity Analytics is a runnable synthetic customer-review application built to demonstrate specification-driven software delivery and provider-neutral AI integration.

The portfolio target is the **full R5 system**, not an earlier deterministic checkpoint. R5 runs all three Stage-1 detector families, PostgreSQL/pgvector grounding, authenticated multi-operator review and a real Stage-3 model adapter. Stage 3 can be backed by OpenAI or a local LM Studio model without changing the application-owned contracts around it.

> **Scope:** the data is synthetic. Detector scores and generated analyses are reviewer signals for this demonstrator, not calibrated production AML decisions and not assertions of wrongdoing.

## Full R5 demonstrator

R5 combines:

- Bayesian, fuzzy and packaged Random Forest Stage-1 evidence;
- local MiniLM embeddings and PostgreSQL/pgvector Stage-2 policy retrieval;
- authenticated review and retained analysis history;
- a bounded application-owned evidence envelope;
- provider-selectable Stage-3 advisory synthesis;
- explicit detector, retrieval, model and prompt provenance;
- React + TypeScript UI and Playwright acceptance evidence.

```text
source activity + source risk
          │
          ▼
Bayesian + fuzzy + Random Forest evidence
          │
          ▼
MiniLM + pgvector policy grounding
          │
          ▼
bounded application-owned evidence envelope
          │
          ▼
OpenAI or local LM Studio synthesis
          │
          ▼
validation + retained provenance/history
```

### Run R5 with OpenAI

Set the repository-scoped credential variable and start the complete topology directly with Compose:

```bash
export SPECGRAPH_OPENAI_API_KEY='...'
export OPENAI_MODEL='gpt-5-mini'
docker compose \
  -f compose.r5.yaml \
  -f compose.r5.openai.yaml \
  up -d --build --wait
```

Open <http://localhost:8088/>.

### Run R5 with a local LM Studio model

Expose the LM Studio OpenAI-compatible endpoint on an address reachable from Docker, then run:

```bash
export SPECGRAPH_LOCAL_BASE_URL='http://HOST_REACHABLE_FROM_DOCKER:1234/v1'
export SPECGRAPH_LOCAL_MODEL='ministral-3-8b-instruct-2512'
docker compose -f compose.r5.yaml up -d --build --wait
```

The same application topology and the same three Stage-1 detectors are used. Only the Stage-3 provider changes.

The two synthetic demo operators are:

| Operator | Password |
| --- | --- |
| `operator-alpha` | `alpha-demo-2026` |
| `operator-beta` | `beta-demo-2026` |

A useful full-flow customer is:

```text
44444444-4444-4444-4444-444444444444
```

Run an analysis, inspect all three detector artifacts, inspect pgvector grounding and model provenance, then reload the page to confirm retained history.

Stop the topology with the same Compose surface used to start it. For OpenAI:

```bash
docker compose -f compose.r5.yaml -f compose.r5.openai.yaml down -v
```

For LM Studio:

```bash
docker compose -f compose.r5.yaml down -v
```

No launcher script is required for the public demo path. Compose is the entry point.

## Demo screenshots

The full R5 screenshot is the primary visual fallback if a live provider is unavailable during review.

### R5 full ensemble + pgvector + local-model provenance

![R5 full ensemble customer review](docs/reviewer/screenshots/R5_lmstudio_ensemble_customer_444.png)

[Open the R5 screenshot at full size](docs/reviewer/screenshots/R5_lmstudio_ensemble_customer_444.png)

The earlier R4 captures are retained as comparison/fail-safe evidence rather than the main demonstration.

### R4 deterministic baseline

![R4 deterministic baseline](docs/reviewer/screenshots/R4_baseline_customer_444.png)

### R4 Bayesian checkpoint

![R4 Bayesian checkpoint](docs/reviewer/screenshots/R4_bayesian_customer_444.png)

Screenshot provenance is recorded in [`docs/reviewer/screenshot-manifest.md`](docs/reviewer/screenshot-manifest.md).

Historical R0-R4 checkpoint publication and ring-replay machinery is preserved by the immutable [`submission-v1`](https://github.com/jdoe-dev-159753/specgraph-reference-app/tree/submission-v1) snapshot and Git history rather than kept active in the portfolio CI surface.

## Architecture

![Hexagonal architecture, ports and adapters](docs/assignment/SDD/diagrams/hexagonal-architecture.svg)

The backend is a Java 21 / Spring Boot modular monolith with explicit hexagonal boundaries. Frameworks and providers sit behind application-owned ports rather than defining durable domain contracts.

Key boundaries include:

- `CustomerActivityPort` for activity and source-risk reads;
- `RiskSignalDetectorPort` for interchangeable derived Stage-1 evidence;
- `PolicyKnowledgePort` for grounded policy retrieval;
- `AnalysisModelPort` for Stage-3 synthesis;
- `AnalysisHistoryPort` for retained completed analyses and provenance.

The application deliberately distinguishes source facts, derived detector evidence, retrieved grounding and generated advisory text. A language model cannot silently promote its output into source truth.

## Verification

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

The active CI surface is deliberately small:

- [`application-ci`](.github/workflows/application-ci.yml) for controlled-design checks, backend verification, frontend build, and both local/OpenAI R5 Compose contracts;
- [`r4-acceptance-ci`](.github/workflows/r4-acceptance-ci.yml) for the retained deep R4 browser/failure-path fallback;
- [`r5-release`](.github/workflows/r5-release.yml) for the final R5 image and browser proof;
- [`plantuml-diagrams`](.github/workflows/plantuml-diagrams.yml) for controlled diagram consistency.

JaCoCo remains part of ordinary Maven verification. Repository-wide coverage aggregation is tracked separately in #488 so the eventual badge reflects the cleaned executable source set rather than a delivery-era LOC count.

## Review evidence

- [SRS: requirements and acceptance semantics](docs/assignment/SRS/SRS.md)
- [SDD: architecture, modules and trust boundaries](docs/assignment/SDD/SDD.md)
- [Architecture decisions](docs/assignment/ADR/)
- [V&V strategy and evidence model](docs/assignment/VV/VV.md)
- [OpenAPI contract](backend/src/main/resources/static/openapi.yaml)
- [Architecture figures](docs/reviewer/architecture-figures.md)
- [Screenshot provenance](docs/reviewer/screenshot-manifest.md)
- [R5 reviewer guide](docs/reviewer/r5-runtime.md)
- [R4 fallback gallery](docs/reviewer/r4-gallery.md)
- [Current presentation](docs/presentation/output/SpecGraph_presentation_working_v0.8.pptx)

The original assignment state remains preserved by the immutable [`submission-v1`](https://github.com/jdoe-dev-159753/specgraph-reference-app/tree/submission-v1) tag. Portfolio cleanup happens after that boundary, so the original delivery remains auditable without forcing every delivery-era control to stay active forever.

## Repository map

```text
backend/          Spring Boot application, ports, adapters and tests
frontend/         React / TypeScript reviewer UI
e2e/              Playwright acceptance scenarios
docs/assignment/  controlled requirements, design, ADR and V&V artifacts
docs/reviewer/    reviewer evidence and runtime notes
docs/presentation current presentation source and output
docker/           application and test container recipes
scripts/          retained verification/runtime helpers not replaced cleanly by native tools
```

## License

See [LICENSE](LICENSE). The repository is published for evaluation and review under its stated proprietary evaluation terms.
