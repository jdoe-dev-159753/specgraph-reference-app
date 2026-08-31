# Verification & Validation Strategy — Customer Activity Analytics

**Document ID:** `CAA-VV-001`  
**Human requirements input:** [`../SRS/SRS.md`](../SRS/SRS.md)  
**Machine requirements input:** [`../SRS/requirements.yaml`](../SRS/requirements.yaml)  
**Human design input:** [`../SDD/SDD.md`](../SDD/SDD.md)  
**Machine design input:** [`../SDD/design-map.yaml`](../SDD/design-map.yaml)  
**Architecture decisions:** [`../ADR/`](../ADR/)  
**Machine-readable verification catalogue:** [`verification.yaml`](verification.yaml)

This document is the canonical human-readable V&V strategy for the reference application. It is intended to be read end-to-end. `verification.yaml` remains the stable machine-readable obligation catalogue; executable tests and checks own current pass/fail evidence.

## 1. Authority and purpose

This document defines durable application-specific V&V policy. It does **not** record current pass counts, coverage percentages, or a hand-maintained requirement status matrix.

Authoritative layers are separated deliberately:

1. the SRS owns requirements, invariants, constraints, acceptance criteria and delivery requirements;
2. the SDD/ADRs own current design, contracts, interfaces and topology;
3. `verification.yaml` owns stable verification-obligation identity and intended evidence linkage;
4. executable tests/checks own current machine-verification evidence;
5. GitHub issues/PRs own work lifecycle and missing-verification work;
6. generated traceability/status views may summarize those authorities but do not replace them.

The reusable harness owns generic requirement/design/test graph validation. The reference app supplies domain requirements, design identities, test markers and evidence. It shall reuse the harness traceability capability and mature marker plumbing rather than invent another local traceability engine.

## 2. Verification obligations at a glance

The baseline currently defines ten stable obligations. This table is a human reading view of `verification.yaml`, not a second machine authority and not a statement that the evidence already passes.

| Obligation | Concern | Primary evidence level |
| --- | --- | --- |
| `VFY-CUSTOMER-READ-001` | Customer lookup, CARD/PAYMENT/CRYPTO activity and risk evidence on the operator dashboard | focused UI E2E + HTTP acceptance + port/integration |
| `VFY-AUTH-001` | Multiple authenticated operators and protected capabilities | Spring Security integration + HTTP acceptance |
| `VFY-ANALYSIS-CONTRACT-001` | Structured risk level, findings and recommendations | contract/unit + acceptance |
| `VFY-RAG-001` | Relevant policy grounding, explicit absent-grounding behavior and provenance | port/integration + failure-path acceptance |
| `VFY-HISTORY-001` | Persisted analysis and authenticated later review with operator/time attribution | PostgreSQL integration + acceptance |
| `VFY-REPRODUCIBILITY-001` | Clean-checkout local/demo startup and deterministic project assembly | deployment/Compose + smoke validation |
| `VFY-DETERMINISM-001` | Mandatory baseline without a live external LLM | deterministic adapter acceptance |
| `VFY-FAILURE-PATHS-001` | Authentication, grounding, model/result and persistence failures do not masquerade as success | explicit failure-injection acceptance/integration |
| `VFY-CONFIDENTIALITY-001` | External-model transmission remains opt-in and deterministic local behavior remains available | configuration + negative integration |
| `VFY-DELIVERY-001` | Repository/README, short demo, LLM-choice summary and agent-instruction summary | artifact checks + executable demo path + focused human validation |

Exact requirement, acceptance-criterion, invariant, delivery and design IDs for each obligation remain in [`verification.yaml`](verification.yaml).

## 3. Verification levels

### Unit and property verification

Use for project-owned domain values, structured result validation, deterministic synthetic generation, specialization invariants and other logic whose contract is independent of Spring/React/PostgreSQL. Property tests are preferred where invariants range over many generated examples.

### Port and contract verification

Each stable project-owned port receives contract tests that can run against the deterministic baseline adapter and the production-like adapter. The purpose is substitution safety: changing `SyntheticActivityAdapter` to JPA or deterministic analysis to a live provider must not change the application contract.

### Integration verification

Use real infrastructure at boundaries where the design depends on infrastructure semantics: Spring Security, HTTP serialization, JPA/Flyway/PostgreSQL, pgvector retrieval and analysis-history persistence. Integration tests verify boundaries, not framework internals.

### Acceptance verification

Acceptance scenarios are derived from SRS acceptance criteria and exercise observable behavior through the highest practical public boundary. They remain distinct from implementation-specific test names.

The dashboard criteria `AC-CUST-001`, `AC-ACT-001`, `AC-ACT-002` and `AC-RISK-001` explicitly concern what an operator can select and see. `VFY-CUSTOMER-READ-001` therefore requires focused UI/E2E evidence in addition to API acceptance. A healthy API cannot by itself prove that the React route, selection action or rendering presents the required activity and risk information. API-level acceptance remains sufficient where browser rendering adds no semantic evidence.

### Architecture verification

Mechanically check dependency direction and forbidden coupling: project-owned domain/application contracts must not depend on Spring/JPA/provider-specific types, and adapter substitution must remain possible behind the declared ports. Spring Modulith/architecture checks are executable evidence for the modular-monolith boundary, not a substitute for behavioral tests.

### Failure-path verification

Negative behavior is first-class evidence. Authentication rejection, unknown customer, absent grounding, provider/model failure, invalid structured output and persistence failure must be exercised where their requirement/acceptance contract exists. A test suite containing only happy paths is not sufficient evidence for `NFR-RES-001`.

In particular, failure injection must demonstrate that:

- absent relevant policy evidence is explicit and does not fabricate grounding provenance;
- model/provider failure does not create a completed history entry;
- invalid structured output is rejected before persistence;
- persistence failure is surfaced and cannot be represented as retained history.

### Deterministic baseline verification

Mandatory acceptance must run without a live external LLM. The deterministic/static adapters are therefore verification infrastructure, not merely demo shortcuts. Live-provider checks, when configured, are supplemental and may never become the sole proof of mandatory behavior.

### Delivery verification

The four source delivery requirements are part of the controlled baseline rather than administrative footnotes. `VFY-DELIVERY-001` covers them explicitly:

- `DEL-001`: verify that the repository contains the reviewer-facing README and that documented entry points resolve;
- `DEL-002`: exercise the documented short demo path from a clean/reproducible deployment and retain focused human evidence that the intended scenario is demonstrable;
- `DEL-003`: verify that the controlled reviewer material contains the LLM/provider choice summary and its rationale;
- `DEL-004`: verify that the controlled reviewer material contains the agent-instruction summary needed to understand how AI-assisted development was governed.

The executable demo path uses the same Docker Compose topology as application CI and the mandatory local baseline. `docker compose up` must not merely start containers: the frontend launcher waits for backend health, starts the UI, verifies frontend readiness and prints a browser-usable `Demo ready: <URL>` line. The default is `http://localhost:5173/`; a dedicated Docker-capable demonstration VM supplies `DEMO_URL` with the address actually reachable from the reviewer browser. The value changes presentation/entry-point configuration, not service-to-service topology.

Foreground interactive execution and detached/persistent execution (`docker compose up -d`) are two operating modes of the same topology. DNS, TLS termination, reverse proxying and router forwarding are not implicit acceptance prerequisites for J1 and require separate evidence if introduced later.

Mechanical artifact checks should prove existence and resolvable references where possible. Human validation proves communicative adequacy where a Boolean file-exists check would merely certify that bytes occupy disk space.

### Human validation

A human reviewer validates concerns that automation cannot prove economically: dashboard comprehensibility, SRS/SDD/diagram intelligibility, the short demo flow, and whether the assembled evidence actually tells a coherent engineering story. Human review supplements executable evidence; it does not turn subjective approval into a substitute for deterministic checks.

## 4. Requirement and design linkage

`verification.yaml` defines stable verification-obligation IDs. An obligation is not a claim that a test already passes. It states **what evidence must exist** and which requirement/acceptance/design identities it is intended to verify.

Implementation PRs satisfy obligations by adding executable evidence carrying the stable obligation/requirement IDs. Until that happens, generated current-status views shall report the obligation as missing/pending from executable evidence. The durable strategy remains unchanged merely because a test temporarily fails or passes.

The SDD is part of the verification input rather than background decoration: tests at port, integration, architecture and deployment levels verify specific design seams such as `CustomerActivityPort`, `AnalysisHistoryPort`, authenticated HTTP boundaries, JPA/PostgreSQL substitution and the Compose topology.

![Verification evidence flow](diagrams/verification-evidence-flow.svg)

[PlantUML source](diagrams/verification-evidence-flow.puml)

## 5. Marker and harness integration

When the corresponding harness capability is available, pytest-side verification should evaluate/reuse `pytest-requirements` marker semantics first, consistent with `specgraph-harness#81`. SpecGraph-specific code owns only catalogue/graph consistency that a generic pytest marker plugin cannot know.

The consumer contract should be able to reject at least:

- unknown or superseded requirement IDs in test evidence;
- design references to unknown controlled design identities;
- a normative or delivery requirement with neither its required obligation/evidence nor an explicit missing-verification work item;
- stale generated status presented as authority.

The app must not implement a second generic RTM renderer or pytest marker framework merely because Python packages occasionally fail to flatter our sense of originality.

## 6. Evidence policy

Evidence should be cheap to reproduce and close to the behavior it proves:

- unit/property evidence: normal test runner output plus markers;
- HTTP/security evidence: integration/acceptance tests;
- database evidence: migration + repository/integration tests against real PostgreSQL where semantics matter;
- architecture evidence: deterministic dependency/import/module checks;
- UI evidence: focused E2E assertions for dashboard acceptance plus human review of comprehensibility;
- provider evidence: deterministic adapter for mandatory baseline, optional live-provider smoke evidence separately tagged;
- deployment evidence: clean-checkout Compose startup/readiness and the same documented browser-ready demo path locally or on the configured Docker VM;
- delivery evidence: repository artifact/link checks plus focused human validation for the demo and reviewer-facing summaries.

No manually copied pass count is authoritative. If a human-readable matrix is useful, generate it from controlled catalogues and executable evidence.

## 7. Exit criterion for this baseline

This V&V baseline is complete when:

- every normative SRS requirement has one or more stable verification obligations in `verification.yaml`;
- every SRS delivery requirement has one or more stable verification obligations;
- every SRS acceptance criterion is linked to at least one obligation;
- invariants and safety constraints that need independent verification are linked explicitly;
- dashboard acceptance criteria that require operator-visible behavior include focused UI/E2E evidence;
- obligations name the intended evidence level without pretending executable tests already exist;
- current verification status can later be derived from executable evidence rather than edited into this document;
- the strategy is understandable from `VV.md` without opening YAML merely to discover what is meant to be verified;
- the embedded verification-flow SVG is committed alongside its PlantUML source;
- no harness-generic traceability mechanism has been duplicated in the reference app.
