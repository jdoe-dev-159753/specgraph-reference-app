# Customer Activity Analytics presentation

`output/Customer_Activity_Analytics_final.pptx` is the final 20-slide reviewer deck for GitHub issue #229. The first 12 slides present the product and delivery method. Eight technical appendix slides retain mechanism-level detail and presenter notes.

The deck reflects the delivered repository state: Hibernate/JPA business persistence with Flyway-owned schema, separate pgvector retrieval, the packaged 31-tree Tribuo Random Forest, and typed deterministic, OpenAI and LM Studio Stage-3 backends. The Random Forest score remains an uncalibrated vote share. The four-scenario fixture cannot support a defensible production performance benchmark, so calibrated heterogeneous fusion is excluded from the final scope.

The six retained image crops come from Playwright artifacts. If the live demonstration is unavailable, those screenshots plus `docs/reviewer/screenshot-manifest.md` are the mandatory fallback. A recorded walkthrough is optional. The conceptual WorkGraph, delivery, milestone and burn-up diagrams explain management views without requiring a private GitHub Project capture.

`build-deck.mjs` rebuilds the deck with the bundled presentation runtime. `VISUAL_SYSTEM.md` documents the preserved visual language and `assets/licenses/LUCIDE-LICENSE.txt` retains the icon licence. Private `.build` output, raw artifacts, archives and superseded deck versions are intentionally excluded from delivery.
