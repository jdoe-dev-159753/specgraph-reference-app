# R5 authenticated composite + LM Studio runtime

R5 is a reviewer-clickable integration of already delivered adapters. It runs the authenticated R4 shell with PostgreSQL/pgvector, the ordered detector selection `BAYESIAN,FUZZY,RANDOM_FOREST`, and local Stage-3 synthesis through LM Studio. It does not replace or change any R4 variant.

## Start on `watch-infra-01`

LM Studio must expose its OpenAI-compatible server to the Docker host. On the current private Hyper-V network, Windows is `10.77.0.1`, WatchInfra is `10.77.0.31`, and LM Studio listens on port `1234`. The application also accepts the link-local address reported by LM Studio (`169.254.123.79`), but prefer `10.77.0.1` because it shares WatchInfra's routed subnet.

Copy this block on WatchInfra. It stops immediately if the VPS cannot reach the configured Ministral model:

```bash
docker login ghcr.io -u jdoe-dev-159753
curl -fsS http://10.77.0.1:1234/v1/models
export SPECGRAPH_LOCAL_BASE_URL=http://10.77.0.1:1234/v1
export SPECGRAPH_LOCAL_MODEL=ministral-3-8b-instruct-2512
export R5_BIND_ADDRESS=10.77.0.31
export R5_PORT=8088
docker compose -p specgraph-r5 \
  -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:r5 \
  up -d --wait --no-build --pull always
docker compose -p specgraph-r5 \
  -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:r5 ps
```

Open `http://10.77.0.31:8088/`. If `10.77.0.1` is not routable from the VPS, repeat the initial `curl` with `169.254.123.79` and use the address that returns `/v1/models`. Keep any Windows firewall allowance scoped to WatchInfra. LM Studio authentication is disabled for this local demonstration; no API token or OpenAI cloud credential is required.

The registered Compose package starts one R5 application image plus PostgreSQL/pgvector and the writable embedding cache initializer. From a repository checkout, `./scripts/r5-runtime-up.sh` adds the strict automated acceptance: it checks `/models`, establishes an authenticated operator session, runs one bounded analysis for customer `44444444-4444-4444-4444-444444444444`, validates the three detector artifacts, pgvector grounding, model/prompt/runtime identities, `backendIdentity=local`, `externalTransmission=false`, and the request budget, then prints the reviewer URL. It pulls the registered image by default; set `R5_SOURCE_BUILD=true` only for a deliberate source rebuild.

The port binds to `127.0.0.1:8088` by default. The WatchInfra command deliberately binds `10.77.0.31:8088` so the Windows browser can reach it without exposing the fixture credentials publicly. Sign in with:

- user: `operator-alpha`
- password: `alpha-demo-2026`

Select a synthetic customer and run an analysis. The retained result contains each detector's distinct evidence, pgvector-backed policy grounding, and LM Studio Stage-3 provenance. The interface renders the backend, model, prompt and external-transmission fields while the retained API payload remains authoritative.

When using the repository launcher, inspect or stop the isolated runtime with:

```bash
./scripts/r5-runtime-status.sh # also rechecks the configured LM Studio model
./scripts/r5-runtime-down.sh   # retains the downloaded embedding cache
# ./scripts/r5-runtime-down.sh --purge  # explicitly deletes that cache volume
```

Set `R5_PORT` to change the published port. The launcher intentionally clears ambient OpenAI credentials, projects the optional LM Studio token only into this R5 service, and uses the distinct `specgraph-r5_session` browser cookie.

The runtime projects a 4,096-token context window, a 512-token maximum output and a 256-token transport/tokenizer margin by default. `SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS`, `SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS` and `SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS` can override those limits. The launcher validates the response's system, user and schema estimates, recomposes their input subtotal, then requires `estimated input + transport margin + maximum output = estimated total <= context window`. Its successful manifest uses the returned model, prompt, runtime and estimator values rather than configured labels. Status reports configured limits and LM Studio reachability but honestly marks response-only fields `not-evaluated-by-status` because it does not create another analysis.

The embedding model and tokenizer are downloaded from their configured public GitHub URLs the first time the cache is populated, so the initial start requires outbound network access even though analysis data stays local. Compose persists those public artifacts in the `r5-embedding-cache` named volume across normal stops. Use `--purge` only when a deliberate re-download is acceptable.

## Why the full composite is the one R5 configuration

The four scenarios were deliberately written as an ordinal story: ordinary `222` < mixed reviewer seed `111` < growing cross-border `333` < dense mixed-risk `444`. The Bayesian detector follows that order with well-separated deterministic values: `0.028`, `0.420`, `0.594`, `0.901`. The fuzzy rule surface produces `0.050`, `0.936`, `0.933`, `0.937`; it distinguishes ordinary from elevated evidence but saturates the three elevated scenarios and slightly reverses `111`/`333`. Follow-up issue [#441](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/441) owns the bounded post-R5 redesign needed to restore graded fuzzy discrimination without changing R5 delivery scope. The Random Forest vote share demonstrates a packaged learned adapter, but its tiny synthetic training generator does not make its number a better oracle.

R5 therefore retains all three artifacts without averaging them. If an interview comparison needs one detector whose score best mirrors the crafted scenario progression, use the Bayesian artifact. This is an internal consistency observation, not an out-of-sample accuracy or calibration claim.

## Evidence boundary

The Stage-1 composite preserves heterogeneous child evidence in configured order. Its Random Forest vote share, Bayesian posterior-tail signal and fuzzy score keep distinct semantics: the composite is **not** a calibrated fused probability. The four synthetic customer scenarios do not support an accuracy-uplift, AML-performance or production-readiness claim. LM Studio provides advisory grounded synthesis; it never becomes source-risk truth. Its response provenance must record `backend=local`, `runtime=lmstudio/llama.cpp` and `externalTransmission=false`. Token counts use CL100K with a 25% safety uplift plus the configured margin; this is a conservative approximation, not the exact Ministral tokenizer. The first-time public artifact download sends no customer or analysis payload.
