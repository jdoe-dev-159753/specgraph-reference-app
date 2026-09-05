# Screenshot promotion manifest

Reviewer screenshots are promoted from authentic workflow artifacts only. The PNG pixels are never reconstructed or edited for evidence purposes.

## Existing retained captures

### R1 / R2 / R3

- workflow run: `application-ci` run `33623762034` (`#329`);
- artifact id: `9844165175`;
- artifact name: `r3-f73bdf4bd9a8122c686797f1f993c28e5162b156-r2-587027b7c80339998ab49e879afefcc692e6d052-r1-e2a41694290ef8b03e4485ca1a2fe2c5664d1941-evidence`;
- artifact digest: `sha256:cea364ee39f5b5e28f358840e7ef84039dce86e1610970d177e2dc509b13ee6e`;
- R1 customer: `11111111-1111-1111-1111-111111111111`;
- R2 customer: `22222222-2222-2222-2222-222222222222`;
- R3 customer: `33333333-3333-3333-3333-333333333333`.

The artifact has been re-downloaded during the reviewer-gallery preparation rather than relying on an old local copy.

### Deterministic R4

- workflow run: complete R4 flow `33653841308`;
- source SHA: `9d44021d95ea052d19ff67152f3af093c4cf8b49`;
- artifact id: `9856114241`;
- artifact name: `r4-complete-flow-9d44021d95ea052d19ff67152f3af093c4cf8b49`;
- artifact digest: `sha256:c86f7b12e82e120914818058fe18e1c32e400dfd4b8200ff3f2ead5843053b77`;
- customer: `44444444-4444-4444-4444-444444444444`.

This artifact has also been re-downloaded during reviewer-gallery preparation.

## Promoted configuration-sensitive R4 captures

`r4-gallery-ci` run `33783606658` completed successfully from source SHA `08dc7a81c920d9908fb83807292c27f55b3f6568`. Each variant ran the complete browser proof against its own PostgreSQL/pgvector topology and asserted the advertised detector/retrieval/model provenance **before** taking the screenshot.

- baseline artifact id `9904939931`, digest `sha256:d5c863d47557bc10aeddca73f2cd4830f4be7a06e42629fa129c76bd2452a81b`;
- Bayesian artifact id `9905134533`, digest `sha256:50d0e84ddbbfc9be5ada38d6f7e4f2bfdcf19dccea13731fd8301425cebb11e3`.

The artifacts were re-downloaded and their exact PNG payloads promoted unchanged:

- `docs/reviewer/screenshots/R4_baseline_customer_444.png`, PNG SHA-256 `badfab38f6f3e6561875b1e5d1636bac7d7a264f5fea2e4e44b833011a4402f1`;
- `docs/reviewer/screenshots/R4_bayesian_customer_444.png`, PNG SHA-256 `beaec890414350b10fc68ea1fb29fc158aa3f31bc6bf3010f72002d8802fa148`.

The baseline has no Stage-1 detector evidence; the Bayesian capture retains `beta-binomial-review-elevation-v1`. Both prove pgvector + `all-MiniLM-L6-v2`, deterministic Stage 3 and no external transmission.

## R5 full-composite capture

`r5-release` first proves the exact source with an authenticated browser flow, the three ordered Stage-1 artifacts, pgvector grounding, local/OpenAI-compatible Stage 3, retained history and the captured model-boundary request. It then uploads `r5-lmstudio-ensemble-<sha>-run-<run>-attempt-<attempt>` and publishes the same exact-head application and Compose candidate to GHCR.

The workflow endpoint is a deterministic LM Studio contract double. The separate WatchInfra rehearsal supplies the actual LM Studio hardware/log proof. After the first successful R5 run, the workflow PNG is promoted unchanged into `docs/reviewer/screenshots/R5_lmstudio_ensemble_customer_444.png`, and its run, source SHA, artifact digest and PNG digest are recorded here before the README embeds it.

Record the successful workflow run, exact source SHA and artifact digest beside each promoted image. The root README embeds screenshots only after those files are repository-owned. Do not link ephemeral sandbox paths or expiring Actions download URLs.

The promotion rule remains: configuration-sensitive Playwright assertions first, screenshot artifact second, repository promotion last.
