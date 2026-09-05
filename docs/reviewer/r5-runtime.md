# R5 authenticated composite + LM Studio runtime

R5 is a reviewer-clickable integration of already delivered adapters. It runs the authenticated R4 shell with PostgreSQL/pgvector, the ordered detector selection `BAYESIAN,FUZZY,RANDOM_FOREST`, and local Stage-3 synthesis through LM Studio. It does not replace or change any R4 variant.

## Start on the Linux Docker host

LM Studio must expose its OpenAI-compatible server to the Docker host. Use a private LAN IP literal; the application rejects public addresses and hostnames. Keep the Windows firewall rule limited to that host. The API key is optional when LM Studio authentication is disabled. Enter a configured token without putting it in the shell command or shell history:

```bash
export SPECGRAPH_LOCAL_BASE_URL=http://WINDOWS_LAN_IP:1234/v1
export SPECGRAPH_LOCAL_MODEL=ministral-3-8b-instruct-2512
read -r -s -p 'LM Studio API key: ' SPECGRAPH_LOCAL_API_KEY && printf '\n'
export SPECGRAPH_LOCAL_API_KEY
./scripts/r5-runtime-up.sh
unset SPECGRAPH_LOCAL_API_KEY
```

If LM Studio authentication is disabled, omit the `read`/`export` lines and run `unset SPECGRAPH_LOCAL_API_KEY` before launch. The launcher projects the token into the R5 container through its environment because the adapter requires it, but uses a mode-0600 temporary curl header file so the value is absent from curl arguments. Users with permission to inspect the container or launcher process environment can still recover the token; use a review-only credential and revoke it after the session.

The launcher builds with Maven inside Docker, starts an isolated `specgraph-r5` Compose project, checks that LM Studio's `/models` endpoint exposes the configured model, and establishes an authenticated operator session. It then runs one bounded end-to-end analysis for seed customer `44444444-4444-4444-4444-444444444444`. Success proves the application's container-to-LM-Studio path and requires retained policy grounding, provenance for all three configured detectors, the returned model/prompt/runtime identities, `backendIdentity=local`, `externalTransmission=false`, and the recomposable request-budget metadata described below. The generated analysis is intentionally retained in this isolated review runtime. Any failed Compose start, network call, authentication, analysis or provenance check stops the stack and returns a non-zero status. `R5_PREFLIGHT_TIMEOUT_SECONDS` defaults to 10 seconds for LM Studio and session calls and is constrained to 1-60 seconds. `R5_ANALYSIS_TIMEOUT_SECONDS` defaults to 90 and is constrained to 1-300 seconds.

The port binds to `127.0.0.1:8088` by default, so a browser on the Docker host opens `http://127.0.0.1:8088/`. For a remote reviewer, deliberately set `R5_BIND_ADDRESS=DOCKER_HOST_PRIVATE_IP` before launch, open `http://DOCKER_HOST_PRIVATE_IP:8088/`, and restrict the host firewall rule to the reviewer's source address. Never expose this demonstration port publicly; it uses published fixture credentials. Sign in with:

- user: `operator-alpha`
- password: `alpha-demo-2026`

Select a synthetic customer and run an analysis. The retained result contains each detector's distinct evidence, pgvector-backed policy grounding, and LM Studio Stage-3 provenance. The interface renders the backend, model, prompt and external-transmission fields while the retained API payload remains authoritative.

Inspect or stop the isolated runtime:

```bash
./scripts/r5-runtime-status.sh # also rechecks the configured LM Studio model
./scripts/r5-runtime-down.sh   # retains the downloaded embedding cache
# ./scripts/r5-runtime-down.sh --purge  # explicitly deletes that cache volume
```

Set `R5_PORT` to change the published port. The launcher intentionally clears ambient OpenAI credentials, projects the optional LM Studio token only into this R5 service, and uses the distinct `specgraph-r5_session` browser cookie.

The runtime projects a 4,096-token context window, a 512-token maximum output and a 256-token transport/tokenizer margin by default. `SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS`, `SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS` and `SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS` can override those limits. The launcher validates the response's system, user and schema estimates, recomposes their input subtotal, then requires `estimated input + transport margin + maximum output = estimated total <= context window`. Its successful manifest uses the returned model, prompt, runtime and estimator values rather than configured labels. Status reports configured limits and LM Studio reachability but honestly marks response-only fields `not-evaluated-by-status` because it does not create another analysis.

The embedding model and tokenizer are downloaded from their configured public GitHub URLs the first time the cache is populated, so the initial start requires outbound network access even though analysis data stays local. Compose persists those public artifacts in the `r5-embedding-cache` named volume across normal stops. Use `--purge` only when a deliberate re-download is acceptable.

## Evidence boundary

The Stage-1 composite preserves heterogeneous child evidence in configured order. Its Random Forest vote share, Bayesian posterior-tail signal and fuzzy score keep distinct semantics: the composite is **not** a calibrated fused probability. The four synthetic customer scenarios do not support an accuracy-uplift, AML-performance or production-readiness claim. LM Studio provides advisory grounded synthesis; it never becomes source-risk truth. Its response provenance must record `backend=local`, `runtime=lmstudio/llama.cpp` and `externalTransmission=false`. Token counts use CL100K with a 25% safety uplift plus the configured margin; this is a conservative approximation, not the exact Ministral tokenizer. The first-time public artifact download sends no customer or analysis payload.
