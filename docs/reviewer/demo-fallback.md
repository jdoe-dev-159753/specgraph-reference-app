# R5 reviewer demonstration and fallback evidence

The primary interview path runs Docker Compose on `watch-infra-01` and LM Studio on Windows. The application uses port `8088`; the R0-R4 gallery remains separate evidence and does not substitute for R5.

## Prepare LM Studio on Windows

1. Load `ministral-3-8b-instruct-2512`.
2. Set **Context Length** to `8192` and reload the model.
3. Enable **Serve on Local Network**.
4. Open **Developer > Logs** and keep it visible for the request proof.

## Start the immutable R5 candidate on WatchInfra

Do not start Compose until `/v1/models` returns the loaded Ministral model.

```bash
docker login ghcr.io -u jdoe-dev-159753
curl -fsS http://10.77.0.1:1234/v1/models
export SPECGRAPH_LOCAL_BASE_URL=http://10.77.0.1:1234/v1
export SPECGRAPH_LOCAL_MODEL=ministral-3-8b-instruct-2512
export R5_BIND_ADDRESS=10.77.0.31
export R5_PORT=8088
docker compose -p specgraph-r5 \
  -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:r5-f6b989af9574a8d54249e29ffff2045129d8f127 \
  up -d --wait --no-build --pull always
docker compose -p specgraph-r5 \
  -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:r5-f6b989af9574a8d54249e29ffff2045129d8f127 \
  ps
```

If `10.77.0.1` cannot reach LM Studio, use the link-local address reported by the current Windows host:

```bash
curl -fsS http://169.254.123.79:1234/v1/models
export SPECGRAPH_LOCAL_BASE_URL=http://169.254.123.79:1234/v1
```

Open `http://10.77.0.31:8088/`. Sign in as `operator-alpha / alpha-demo-2026` or `operator-beta / beta-demo-2026`, then search for customer `44444444-4444-4444-4444-444444444444`.

## Ten to fifteen minute reviewer rail

1. State the Customer Care problem and the concrete result.
2. Explain the specification-driven method and the replaceable hexagonal ports.
3. Sign in and open customer `44444444-4444-4444-4444-444444444444`.
4. Show structured activities and source risk evidence as separate facts.
5. Select **Run analysis**.
6. Show the OpenAI-compatible request in LM Studio Developer Logs.
7. Show the Bayesian, fuzzy and Random Forest artifacts retained by Composite.
8. Show synthetic-policy grounding from pgvector and `all-MiniLM-L6-v2`.
9. Show `backend: local`, model `ministral-3-8b-instruct-2512`, prompt provenance and `external transmission: no`.
10. Reload the page and show the retained analysis history.
11. Close with the limits: advisory output, uncalibrated Random Forest vote share, graded fuzzy activation, synthetic policies and no AML-performance claim from four scenarios.
12. Show the GitHub workflow and exact source evidence, then switch to the screenshot fallback only if the live path fails.

The authentic R5 screenshot is `docs/reviewer/screenshots/R5_lmstudio_ensemble_customer_444.png`. It came from `r5-release` run `34020857953`, artifact `9985493952`, exact executable source `f6b989af9574a8d54249e29ffff2045129d8f127`, and PNG SHA-256 `7503a8da09678241d8d06064d3927961c0ec758a14c0254531f18a2c19411a05`. That workflow used a deterministic LM Studio contract double. The manual WatchInfra rehearsal supplies the distinct proof against the real LM Studio process and Developer Logs.

If the live path fails, use the authentic R5 screenshot with `docs/reviewer/screenshot-manifest.md`, then show the R1-R4 captures only to explain capability growth. A recording may supplement the screenshots but remains optional and must disclose its source SHA and date.

## Stop the demonstration

```bash
docker compose -p specgraph-r5 \
  -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:r5-f6b989af9574a8d54249e29ffff2045129d8f127 \
  down -v
```
