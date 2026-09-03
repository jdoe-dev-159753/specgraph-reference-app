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

## New configuration-sensitive R4 captures

PR #255 introduces `r4-gallery-ci`. Each variant runs the complete browser proof against its own PostgreSQL/pgvector topology and asserts the advertised detector/retrieval/model provenance **before** taking the screenshot.

Expected artifacts for each exact PR head:

- `r4-gallery-baseline-<sha>`: no Stage-1 detector evidence; pgvector + `all-MiniLM-L6-v2`; deterministic Stage 3; no external transmission;
- `r4-gallery-bayesian-<sha>`: `beta-binomial-review-elevation-v1` evidence and `DETECTOR_SIGNAL` reference; same pgvector/MiniLM grounding; deterministic Stage 3; no external transmission.

After a green run, download both artifacts, extract the exact PNG produced by Playwright and promote it unchanged into:

```text
docs/reviewer/screenshots/
  R1_customer_111.png
  R2_customer_222.png
  R3_customer_333.png
  R4_baseline_customer_444.png
  R4_bayesian_customer_444.png
```

Record the successful workflow run, exact source SHA and artifact digest beside each promoted image. The root README embeds screenshots only after those files are repository-owned. Do not link ephemeral sandbox paths or expiring Actions download URLs.

Later local-model / Composite-ensemble variants follow the same rule: configuration-sensitive Playwright assertions first, screenshot artifact second, repository promotion last.
