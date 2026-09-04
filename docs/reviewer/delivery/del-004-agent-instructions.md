# DEL-004 — AI development-agent instructions

AI agents were instructed to act as bounded engineering contributors, not as authorities. Repository specifications, accepted design decisions, tests, code, GitHub state, Git history and execution evidence remain authoritative; generated analysis is a proposal or review aid.

The standing repository instructions required agents to:

- work from the controlled requirements and preserve traceability through progressively real, demonstrable vertical slices;
- keep framework, persistence, model-provider and ML-library details behind project-owned hexagonal ports, and prefer established platform or dependency capabilities before custom code;
- represent ownership, lifecycle, dependencies and discoveries in native GitHub issues, pull requests and Project fields rather than duplicating that state in prose;
- keep each pull request conceptually focused, include the tests that prove its change and order dependent changes explicitly;
- use deterministic checks for mechanically decidable properties, leaving AI review to ambiguity, architecture, counterexamples, failure paths and evidence gaps;
- finish mutable work before freezing a candidate commit, request one review for that exact SHA, reconcile material findings and merge only the reviewed head.

These instructions shaped the repository workflow: agents could propose and implement changes in parallel, but acceptance depended on repository-owned evidence and reviewable GitHub changes. The summary deliberately does not reproduce the full governance or an interaction transcript.

## Authorities and verification

- [`AGENTS.md`](../../../AGENTS.md) is the complete, current instruction set; it owns the rules summarized here.
- The [`SRS`](../../assignment/SRS/SRS.md) owns `DEL-004` and the delivery obligation, not the detailed agent workflow.
- The [`SDD`](../../assignment/SDD/SDD.md) owns the resulting architecture and implementation boundaries.
- The [`V&V plan`](../../assignment/VV/VV.md#delivery-verification) owns `VFY-DELIVERY-001` and requires focused human validation of this summary rather than treating subjective explanatory quality as an automated pass.
- Git history and linked GitHub issues/pull requests provide the reviewable record of how the instructions were applied; they remain evidence rather than a second instruction authority.
