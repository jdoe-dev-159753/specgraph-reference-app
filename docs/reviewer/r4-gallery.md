# R4 side-by-side demo gallery

R0-R4 are capability-maturity rings. R4 backend/detector variants remain **R4** and run as separate configured processes, not as pseudo-rings.

Current executable source variants:

| Port | Ring | Stage 1 | Stage 2 | Stage 3 | External transmission |
| ---: | --- | --- | --- | --- | --- |
| 8084 | R4 baseline | no-op | pgvector + local all-MiniLM-L6-v2 | deterministic | no |
| 8085 | R4 Bayesian | Bayesian beta-binomial | pgvector + local all-MiniLM-L6-v2 | deterministic | no |

Reserved optional variants once their owners land:

| Port | Ring | Stage 1 | Stage 2 | Stage 3 | Owner |
| ---: | --- | --- | --- | --- | --- |
| 8086 | R4 local | Bayesian / configured ensemble | same RAG | local LM Studio model | #251 |
| 8087 | R4 external | Bayesian / configured ensemble | same RAG | OpenAI | #207, optional only |

## Bash / Linux / WSL

Baseline:

```bash
docker compose -p specgraph-r4-baseline -f compose.r4.yaml up -d --build --wait
```

Bayesian variant on the adjacent port:

```bash
R4_PORT=8085 R4_PROFILES=r4,bayesian-detector \
  docker compose -p specgraph-r4-bayesian -f compose.r4.yaml up -d --build --wait
```

Open both:

```text
http://localhost:8084/
http://localhost:8085/
```

Stop one variant without touching the other:

```bash
docker compose -p specgraph-r4-baseline -f compose.r4.yaml down -v
docker compose -p specgraph-r4-bayesian -f compose.r4.yaml down -v
```

## PowerShell

Baseline:

```powershell
docker compose -p specgraph-r4-baseline -f compose.r4.yaml up -d --build --wait
```

Bayesian variant:

```powershell
$env:R4_PORT = "8085"
$env:R4_PROFILES = "r4,bayesian-detector"
docker compose -p specgraph-r4-bayesian -f compose.r4.yaml up -d --build --wait
Remove-Item Env:R4_PORT
Remove-Item Env:R4_PROFILES
```

Each Compose project owns an isolated PostgreSQL/pgvector instance and analysis history. Starting or stopping one variant therefore does not mutate the other variant's history.

The `R4_PROFILES` mechanism is explicitly transitional. #163 owns the final typed process-level detector/backend selection factory and will replace profile strings without changing this side-by-side operating model.
