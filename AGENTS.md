# AGENTS.md

These instructions apply repository-wide unless a deeper `AGENTS.md` narrows them.

## Portfolio boundary

`submission-v1` is the immutable record of the original assignment delivery. Do not move, rewrite, or retrofit that tag. Current `main` is the post-submission portfolio edition and may simplify delivery-era tooling when the historical evidence remains recoverable from Git history.

## Engineering authority

Requirements, design decisions, tests, code, Git history and executable evidence are authoritative according to their role. AI output is a proposal or review aid, never a substitute source of truth.

The controlled application documents remain:

- `docs/assignment/SRS/SRS.md` for requirements and acceptance semantics;
- `docs/assignment/SDD/SDD.md` plus `design-map.yaml` for architecture;
- `docs/assignment/ADR/` for material design decisions;
- `docs/assignment/VV/VV.md` and `verification.yaml` for verification intent.

Do not update controlled documents for cosmetic code motion. Reconcile them when a change alters an owned contract, module boundary, dependency direction, runtime topology, evidence semantics, or other architectural decision.

## Work discipline

Use GitHub issues and pull requests for durable work when they add review value. Do not recreate the retired delivery-time WorkGraph/Project-v2 automation or duplicate lifecycle state in prose.

Prefer one bounded conceptual change per pull request. Complete the intended change before running its final checks. If a deterministic check fails, fix that concrete failure; do not start a broad new review or cleanup loop unless the failure provides evidence for it.

No automated Codex review, Project-field reconciliation, or exact-head WorkGraph gate is mandatory for portfolio maintenance. Review effort should be proportional to the risk and scope of the change.

## Architecture and reuse

Preserve strict hexagonal dependency direction. Domain/application semantics depend on project-owned contracts and ports. Spring, persistence libraries, model providers, vector stores, browser frameworks, Docker and other external technologies remain adapters or infrastructure.

Prefer composition to inheritance. Prefer platform or mature library capabilities over project-owned generic infrastructure. Do not add `common`, `shared`, `util`, horizontal `web`, or horizontal `persistence` dumping-ground modules.

Factories, registries and builders must solve a concrete construction or substitution problem. Do not introduce patterns for vocabulary or pattern-count aesthetics.

## Portfolio simplification

Optimize for a reviewer who should understand and run the repository without learning its delivery archaeology.

- keep one obvious default runtime path;
- keep a small set of obvious verification commands;
- remove orphaned or submission-only wrappers when `submission-v1` already preserves their evidence;
- prefer native language tooling behind a small top-level task surface rather than proliferating orchestration languages;
- do not keep a workflow merely because it existed during the assignment;
- do not add tests solely to inflate coverage of trivial glue that should instead be removed.

Coverage is a regression signal. Measure retained substantive executable source with the appropriate native tools and document exclusions rather than manufacturing a vanity percentage.

## Verification

Run the checks mechanically relevant to the change. Typical source verification includes backend Maven tests, frontend type/build checks, architecture tests, and the retained end-to-end acceptance path when runtime behavior changes.

Documentation-only cleanup does not require replaying expensive application workflows unless it changes executable configuration or a mechanically validated contract.

Keep authentic screenshots and historical delivery evidence truthful about the source revision that produced them. Never relabel old evidence as if it came from a newer portfolio revision.
