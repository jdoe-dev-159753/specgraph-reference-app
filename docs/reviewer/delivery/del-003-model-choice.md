# DEL-003 — Model and provider choice

## Decision in one minute

Customer Activity Analytics uses a project-owned `AnalysisModelPort` rather than making one provider part of the application core. The default strategy is the deterministic adapter: it needs no credential or network call, makes the mandatory demo reproducible, and records no external transmission. OpenAI and a private LM Studio runtime are implemented as explicit opt-in strategies behind the same port. Supplying provider settings or credentials configures a strategy but does not select it.

| Stage-3 choice | Intended use | Transmission |
| --- | --- | --- |
| Deterministic (default) | acceptance, offline demonstration and a stable comparison baseline | none |
| LM Studio (opt-in) | private-model comparison on operator-controlled local infrastructure | recorded as non-external |
| OpenAI (opt-in) | deliberate external-provider comparison on permitted synthetic demo data | external and recorded explicitly |

This choice keeps one orchestration path and one structured result contract across all three strategies. A live model remains advisory: it synthesizes supplied source-risk, detector and policy evidence, but it neither becomes the source of risk facts nor writes generated claims back as source evidence. Invalid structure, unsupported citations or failed persistence cannot be represented as a completed retained analysis.

The current configuration provides `gpt-5-mini` as the OpenAI default and `ministral-3-8b-instruct-2512` as the LM Studio default. These are replaceable adapter settings, not application-contract commitments; changing either model does not create a second analysis architecture.

## Bounded model input

The model receives an application-owned `AnalysisEvidenceEnvelope`, not an unbounded customer record or provider-specific object. Complete input totals remain truthful while model-visible detail is selected deterministically. The configured defaults are 25 activities, 20 source-risk facts, 8 detector artifacts and 3 policy artifacts. Selected claims must cite evidence that actually crossed this boundary; provider-specific token limits and redaction remain adapter concerns.

The consequence is intentional: the mandatory result is demonstrable without a live LLM, while optional local and external comparisons can be made without changing application contracts or weakening evidence authority.

## Authorities and verification

- [`ADR-002`](../../assignment/ADR/ADR-002-provider-neutral-analysis.md) owns the provider-neutral decision and the detection-versus-explanation trust boundary.
- The [`SDD`](../../assignment/SDD/SDD.md) owns the implemented strategies, selection semantics, evidence-envelope limits, provenance and failure behavior.
- The [`SRS`](../../assignment/SRS/SRS.md) owns `DEL-003`, confidentiality constraint `CON-AI-002` and the requirements on AI-assisted analysis.
- [`VFY-DELIVERY-001`](../../assignment/VV/VV.md#delivery-verification) requires focused human validation of this summary; executable configuration, opt-out and integration evidence are mapped separately in the V&V plan.
- [`application.yml`](../../../backend/src/main/resources/application.yml) is the executable authority for the current backend and model defaults.
- The [R4 reviewer guide](../r4-gallery.md) shows the runnable deterministic, local and external variants.
