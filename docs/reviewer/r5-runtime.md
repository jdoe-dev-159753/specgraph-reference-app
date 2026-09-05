# R5 authenticated composite + LM Studio runtime

R5 is a reviewer-clickable integration of already delivered adapters. It runs the authenticated R4 shell with PostgreSQL/pgvector, the ordered detector selection `BAYESIAN,FUZZY,RANDOM_FOREST`, and local Stage-3 synthesis through LM Studio. It does not replace or change any R4 variant.

## Start on the Linux Docker host

LM Studio must expose its OpenAI-compatible server to the Docker host. Use a private LAN IP literal; the application rejects public addresses and hostnames. Keep the Windows firewall rule limited to that host. The API key is optional when LM Studio authentication is disabled.

```bash
SPECGRAPH_LOCAL_BASE_URL=http://WINDOWS_LAN_IP:1234/v1 \
SPECGRAPH_LOCAL_MODEL=ministral-3-8b-instruct-2512 \
SPECGRAPH_LOCAL_API_KEY=... \
./scripts/r5-runtime-up.sh
```

The launcher builds with Maven inside Docker, starts an isolated `specgraph-r5` Compose project, checks LM Studio's `/models` endpoint, and establishes an authenticated operator session. It then runs one bounded end-to-end analysis for seed customer `44444444-4444-4444-4444-444444444444`. Success proves the application's container-to-LM-Studio path and requires retained policy grounding, provenance for all three configured detectors, `backendIdentity=local`, `externalTransmission=false`, and the request-budget metadata described below. The generated analysis is intentionally retained in this isolated review runtime. A failed Compose start, LM Studio, authentication, analysis or provenance check stops the stack and returns a non-zero status. Omit `SPECGRAPH_LOCAL_API_KEY` when no token is configured. `R5_ANALYSIS_TIMEOUT_SECONDS` defaults to 90 and is constrained to 1-300 seconds.

Open `http://DOCKER_HOST:8088/` from the reviewer's browser and sign in with:

- user: `operator-alpha`
- password: `alpha-demo-2026`

Select a synthetic customer and run an analysis. The retained result contains each detector's distinct evidence, pgvector-backed policy grounding, and LM Studio Stage-3 provenance. The interface renders the backend, model, prompt and external-transmission fields while the retained API payload remains authoritative.

Inspect or stop the isolated runtime:

```bash
./scripts/r5-runtime-status.sh # reports ready only for the expected running, healthy container
./scripts/r5-runtime-down.sh
```

Set `R5_PORT` to change the published port. The launcher intentionally clears ambient OpenAI credentials, projects the optional LM Studio token only into this R5 service, and uses the distinct `specgraph-r5_session` browser cookie.

The runtime projects a 4,096-token context window, a 512-token maximum output and a 256-token transport/tokenizer margin by default. `SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS`, `SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS` and `SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS` can override those limits. The successful launch manifest exposes the corresponding `request.*` provenance and the estimated total for the retained analysis; status reports the configured limits but does not run another analysis.

## Evidence boundary

The Stage-1 composite preserves heterogeneous child evidence in configured order. Its Random Forest vote share, Bayesian posterior-tail signal and fuzzy score keep distinct semantics: the composite is **not** a calibrated fused probability. The four synthetic customer scenarios do not support an accuracy-uplift, AML-performance or production-readiness claim. LM Studio provides advisory grounded synthesis; it never becomes source-risk truth. Its provenance records `backend=local`, `runtime=lmstudio/llama.cpp` and `externalTransmission=false`. Token counts use CL100K with a 25% safety uplift plus the configured margin; this is a conservative approximation, not the exact Ministral tokenizer.
