# R4 fallback evidence

R4 is retained as the credential-free fallback for reviewing the application without the richer R5 local-model setup. The active portfolio keeps the R4 Compose topology and consolidated browser acceptance workflow, but no longer carries the delivery-era side-by-side launcher family.

The original `r4-variant-*` and `r4-gallery-*` launchers, their shell regression test, and the specialized `r4-gallery-ci` workflow remain preserved under the immutable [`submission-v1`](https://github.com/jdoe-dev-159753/specgraph-reference-app/tree/submission-v1) tag and Git history.

## Run the current R4 fallback

From a current checkout:

```bash
docker compose -p specgraph-r4 -f compose.r4.yaml up -d --build --wait
```

Open <http://localhost:8084/> and sign in with either synthetic demo operator documented in the root README. The default R4 profile uses PostgreSQL/pgvector grounding, local MiniLM embeddings and deterministic Stage-3 synthesis, so it requires no model-provider credential.

Stop and remove the disposable state with:

```bash
docker compose -p specgraph-r4 -f compose.r4.yaml down -v
```

The retained [`r4-acceptance-ci`](../../.github/workflows/r4-acceptance-ci.yml) workflow is the active executable evidence for the R4 path. It exercises the authenticated browser flow, analysis, pgvector grounding, retained history, deterministic behavior and degradation handling against an exact source revision.

## Preserved side-by-side evidence

The repository keeps the unedited screenshots promoted from the delivered side-by-side R4 proof:

- [`R4_baseline_customer_444.png`](screenshots/R4_baseline_customer_444.png) shows the deterministic baseline with pgvector grounding and retained history;
- [`R4_bayesian_customer_444.png`](screenshots/R4_bayesian_customer_444.png) shows the Bayesian detector variant with the same grounding path.

Their exact workflow run, artifact IDs and digests are recorded in [`screenshot-manifest.md`](screenshot-manifest.md). Those captures are historical evidence from the submitted implementation, not claims that the retired gallery workflow still runs on current `main`.

The assignment SDD and design map intentionally retain the delivered ring/variant design. For the post-submission portfolio runtime, `compose.r4.yaml` plus `r4-acceptance-ci` are the maintained R4 surfaces; R5 remains the primary richer demonstration path.
