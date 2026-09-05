# J5 final controlled-artifact reconciliation

This record captures the final catalogue and work-graph audit for issue
[#146](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/146).
It is a bounded reconciliation record, not a manually maintained replacement
for executable test results or GitHub lifecycle state.

## Frozen implementation anchor

- controlled-document baseline before the final degradation merge:
  `9533211de20dffaf8d041a5faca13712cf8ad003`;
- final executable evidence supplied by PR
  [#417](https://github.com/jdoe-dev-159753/specgraph-reference-app/pull/417):
  `00c7140576f817b7aa7257f45a44797fb430efbb`.

This exact merge commit is the executable implementation anchor for this
reconciliation; this document does not claim that its own documentation-only
commit was the revision executed by PR #417.

## Controlled catalogue audit

The machine-readable authorities were counted and cross-referenced directly:

| Controlled set | Audited result | Reconciliation conclusion |
| --- | ---: | --- |
| Normative requirements | 14/14 | Every `requirements` ID is represented in `requirement_design` and covered by at least one verification obligation. |
| Invariants | 6/6 | Every invariant is covered by at least one verification obligation. |
| Constraints | 3/3 | Every constraint is covered by at least one verification obligation; `CON-AI-002` also has an explicit design mapping. |
| Acceptance criteria | 17/17 | Every acceptance criterion is linked to at least one verification obligation. |
| Delivery requirements | 4/4 | `DEL-001` through `DEL-004` are covered by `VFY-DELIVERY-001`. |
| Verification obligations | 10 | All obligation identities in `verification.yaml` resolve to controlled SRS and design identities. |
| Verification design references | 35 | All 35 distinct design IDs referenced by the obligations resolve in `design-map.yaml`. |
| Architecture decisions | 8 | `ADR-001` through `ADR-008` resolve from the design map to committed decision records. |

This is catalogue coverage, not a claim that every declared evidence method is
automated. Current pass/fail authority remains the executable checks. In
particular, the declared `query_count` method for JPA customer reads remains an
explicit follow-up in
[#421](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/421);
the existing functional, pagination and PostgreSQL integration evidence is not
misrepresented as an N+1 bound.

The R4 policy-retrieval and model-provider ambiguities are now resolved by
committed adapters, selection contracts and verification evidence. Accordingly,
the obsolete `GAP-RAG-001` and `GAP-LLM-001` entries were removed from
`design-map.yaml`; the source ambiguities remain in the SRS as historical input
and are not rewritten as if they had never existed.

## Human validation remains human

Two final obligations intentionally remain human-owned:

- [#229](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/229)
  owns assembly and human review of the final presentation and demo narrative;
- [#148](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/148)
  owns rehearsal of the exact frozen build in the required 10–15 minute window.

Neither is converted into a synthetic automated PASS by this reconciliation.

## Project and work-graph audit

Scheduled Project reconciliation run
[`33945213927`](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/runs/33945213927)
completed successfully at `8ec6cebca7d583cf4281a4c74e206947f46efe8a`.
It exercised the repository's whole-Project lifecycle, delivery-priority and
discovery-disposition projection. Remaining Project-system debt is already
owned by:

- [#193](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/193),
  which tracks serialization of whole-Project reconciliation without
  cancel/restart churn and its final unexplained-blank audit;
- [#49](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/49),
  which tracks the remaining native Project UI, roadmap and auto-add polish.

Those independently reviewable governance items do not invalidate the
requirement-to-design-to-verification catalogue above and are not silently
absorbed into #146.

## Reconciliation result

At the exact #417 merge commit above, the controlled SRS, SDD/ADR and V&V
catalogues form a complete structural chain:
14 requirements, 6 invariants, 3 constraints, 17 acceptance criteria and 4
delivery requirements resolve through 10 verification obligations to 35
controlled design identities and 8 ADRs. The only known method-level evidence
gap is explicitly owned by #421, while final communicative adequacy and demo
rehearsal remain correctly owned by #229 and #148.
