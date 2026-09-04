# R4 side-by-side demo gallery

R0-R4 are capability-maturity rings. R4 backend/detector variants remain **R4** and run as separate configured processes, not as pseudo-rings.

The canonical demo runtime is **`watch-infra-01`**, a Linux host running Docker Compose. Run these commands from a checkout of this repository on that host.

Current executable source variants:

| Port | Ring | Stage 1 | Stage 2 | Stage 3 | External transmission |
| ---: | --- | --- | --- | --- | --- |
| 8084 | R4 baseline | no-op | pgvector + local all-MiniLM-L6-v2 | deterministic | no |
| 8085 | R4 Bayesian | Bayesian beta-binomial | pgvector + local all-MiniLM-L6-v2 | deterministic | no |
| 8087 | R4 external, optional | configured detector | same RAG | OpenAI | yes, only with deliberate credential + backend selection |

Reserved optional variants once their owners land:

| Port | Ring | Stage 1 | Stage 2 | Stage 3 | Owner |
| ---: | --- | --- | --- | --- | --- |
| 8086 | R4 local | Bayesian / configured Composite+ensemble | same RAG | local LM Studio model | #251 |

## Linux commands on `watch-infra-01`

Baseline:

```bash
docker compose -p specgraph-r4-baseline -f compose.r4.yaml up -d --build --wait
```

The repository-owned launcher makes the Stage-3 choice explicit and produces a reviewer manifest:

```bash
./scripts/r4-variant-up.sh baseline 8084 deterministic
OPENAI_API_KEY=... ./scripts/r4-variant-up.sh external 8087 openai
```

`./scripts/r4-gallery-up.sh` starts the baseline and adds the external variant only when `OPENAI_API_KEY` is deliberately present. When the key is absent, it also stops any previously launched external project before reporting the credential-free gallery. `local` is a reserved backend identifier and fails closed until #251 supplies the adapter.

Bayesian variant on the adjacent port:

```bash
R4_PORT=8085 R4_PROFILES=r4,bayesian-detector \
  docker compose -p specgraph-r4-bayesian -f compose.r4.yaml up -d --build --wait
```

Open from a machine that can reach `watch-infra-01`:

```text
http://watch-infra-01:8084/
http://watch-infra-01:8085/
```

Use the host IP instead of the hostname if local DNS does not resolve it.

Inspect the two isolated projects:

```bash
docker compose -p specgraph-r4-baseline -f compose.r4.yaml ps
R4_PORT=8085 R4_PROFILES=r4,bayesian-detector \
  docker compose -p specgraph-r4-bayesian -f compose.r4.yaml ps
```

Stop one variant without touching the other:

```bash
docker compose -p specgraph-r4-baseline -f compose.r4.yaml down -v
R4_PORT=8085 R4_PROFILES=r4,bayesian-detector \
  docker compose -p specgraph-r4-bayesian -f compose.r4.yaml down -v
```

Each Compose project owns an isolated PostgreSQL/pgvector instance and analysis history. Starting or stopping one variant therefore does not mutate the other variant's history.

## CI / screenshot evidence

`r4-gallery-ci` exercises the same baseline and Bayesian configurations on isolated CI ports. Before taking a screenshot, Playwright asserts the advertised configuration:

- baseline: no detector provenance, real pgvector + `all-MiniLM-L6-v2`, deterministic Stage 3, no external transmission;
- Bayesian: `beta-binomial-review-elevation-v1` detector provenance and `DETECTOR_SIGNAL` references, the same pgvector/MiniLM grounding, deterministic Stage 3, no external transmission.

Each successful variant uploads a separate `r4-gallery-<variant>-<sha>` artifact containing the screenshot and a small provenance manifest. Selected PNGs are then promoted unchanged into `docs/reviewer/screenshots/` for the README evidence gallery.

`R4_PROFILES` remains the detector-side selection seam in this stack. Stage 3 is independently selected through the application-owned `specgraph.analysis.backend` dimension (`SPECGRAPH_ANALYSIS_BACKEND` in Compose); provider credentials configure a leaf but never select it. #224/#254 own the later Composite detector topology and calibrated ensemble semantics, while #251 owns the reserved local Stage-3 adapter.
