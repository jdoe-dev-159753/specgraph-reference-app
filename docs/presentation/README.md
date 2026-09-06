# Presentation working deck

`output/SpecGraph_presentation_working_v0.8.pptx` is the 22-slide final review candidate for GitHub issue #229. Earlier local working drafts are not part of the submission.

The answer-first main narrative contains 14 slides, including the cover. Eight technical appendix slides follow. Every slide contains presenter notes structured as an anchor, short script, guardrail, transition, timing, likely reviewer question, direct answer, and source list.

Appendix A uses presentation crops produced from authentic retained Playwright artifacts. `docs/reviewer/screenshot-manifest.md` records the immutable workflow run, artifact identifier and digest for each ring. The R1-to-R3 source PNGs remain in the referenced Actions artifact rather than in the checkout; only the explicitly promoted R4 and R5 source PNGs are repository-owned under `docs/reviewer/screenshots/`. The WorkGraph, Delivery Kanban, Milestones Roadmap, and burn-up management views are explicitly labelled schematic previews because the private GitHub Project could not be captured without an authenticated browser session. They provide context rather than execution evidence; the retained R1-to-R5 artifacts remain authoritative.

All diagrams use a left-to-right primary reading direction. Bidirectional connectors are reserved for mediated hand-offs and the operator journey closes with an explicit return to persisted history. Version 0.3 replaced the Bayesian box sequence with prior and posterior Beta distributions, showed the then-current linear right-shoulder fuzzy functions, and redrew fusion and verification as attached data-flow mappings. Version 0.4 applies a real diagram asset system based on Lucide icons to the operator loop, analysis flow, architecture, control plane, management views, quality loop, conclusion and technical data flows. Version 0.5 adds a horizontal analytical implementation landscape that separates landed adapters, active pull requests and planned options; it also distinguishes the Random Forest's internal weighted tree ensemble from heterogeneous detector composition and future calibrated late fusion. Version 0.6 adds the first delivery estimate and separates observed feedback-loop timings from the human-only parametric baseline. Version 0.7 adds the delivered R5 browser evidence, reconciles R5 status across the main narrative and appendix, and selects the exact-workflow replay estimate: 140 to 170 human workdays, midpoint 155, or about 22 times seven calendar days. Version 0.8 makes R5 dominant in the grouped R1-to-R5 browser-evidence gallery and aligns Appendix D with the delivered v3 overlapping fuzzy partition. The earlier 115-day estimate remains explained as the lower-bound counterfactual for reproducing only the final scope with the design and solution path already known. The selection and tool-routing rules are documented in `VISUAL_SYSTEM.md`. Screenshot captures are aspect-contained within their evidence frames. R5 runtime delivery does not imply production AML performance or calibrated detector scores.

The deck uses a reserved human-readable release label, `submission-v1`. Create the real Git tag only when the demonstrated application, fallback evidence, and final deck are frozen together.

## Rebuild

The checked-in public toolchain requires Node.js 22 or later and pnpm 11.19.0. From `docs/presentation/`:

```text
pnpm install --frozen-lockfile
pnpm build
```

`package.json` and `pnpm-lock.yaml` pin Sharp, JSZip and Lucide. That command produces the 22-slide PPTX from repository-owned source and image crops without a Codex-specific runtime. When the bundled presentation-validation runtime is available, the same generator additionally performs the artifact-tool import and structural/layout finalization used for the committed candidate.
