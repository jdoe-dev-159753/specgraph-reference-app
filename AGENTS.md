# AGENTS.md

These instructions apply repository-wide unless a deeper `AGENTS.md` narrows them.

## Engineering authority

Specifications, design decisions, tests, code, Git history, GitHub metadata, review evidence, and execution evidence are authoritative according to their role. AI output is a proposal or review aid, never a substitute source of truth.

## GitHub-native work graph

GitHub owns mechanically representable work state. Use native issue state and close reasons, assignees, milestones, issue types, Projects fields, parent/sub-issues, blocked-by/blocking dependencies, duplicate relations, and Development/closing PR links whenever those semantics exist.

Do not encode lifecycle, hierarchy, dependency, duplicate, implementation ownership, or exclusive workflow state as prose fields, title prefixes, checkboxes, comments, or labels. Labels are non-exclusive semantic tags such as `architecture`, `backend`, `frontend`, `database`, `security`, `ai`, `rag`, `deployment`, `testing`, `documentation`, and `discovery`; they never emulate an enum.

A custom Project field is justified only for a genuine independent closed dimension that GitHub does not represent natively, such as delivery priority or discovery disposition. Use a single-select field rather than prose or mutually-exclusive labels.

Free text explains purpose, scope, evidence, rationale, assumptions, acceptance criteria, non-goals, and reviewability. It does not duplicate the work graph.

### Delivery priority

`Delivery priority` is a planning dimension with exactly `MANDATORY`, `MUST_HAVE`, and `NICE_TO_HAVE`.

- A root planning issue owns the explicit delivery-priority decision.
- A sub-issue inherits delivery priority recursively from its native `Parent issue` chain. Do not manually fork the value on descendants.
- A pull request has no synthetic parent issue. Its delivery priority is derived from the complete set of native closing/Development owner issues, using the highest urgency when more than one owner exists: `MANDATORY > MUST_HAVE > NICE_TO_HAVE`.
- If a parent or owner relation is cross-repository or missing from the Project, do not infer from a partial graph. Surface the inconsistency for reconciliation.
- Never infer delivery priority from title wording, labels, milestone, dates, or prose.

### Discovery disposition

`Discovery disposition` applies only to material discovery issues. Ordinary planned issues and pull requests intentionally leave this field not applicable/blank.

Every material discovery requiring reconciliation must be represented by an issue carrying the non-exclusive `discovery` label and exactly one typed Project value:

`IN_SCOPE | FOLLOW_UP | ALREADY_TRACKED | NON_ACTIONABLE`

Map the value to native GitHub facts:

- `IN_SCOPE`: the current corrective PR owns the discovery and closes it;
- `FOLLOW_UP`: the discovery remains open as independently reviewable work;
- `ALREADY_TRACKED`: use a native duplicate relation and close as duplicate;
- `NON_ACTIONABLE`: close as not planned.

Do not use a disposition value on ordinary PR rows merely to eliminate blanks.

### Project status

Project `Status` is a projection of native lifecycle/ownership facts, not an independent workflow state:

- closed issue -> `Done`;
- open issue with an open owning PR -> `In Progress`;
- other open issue -> `Todo`;
- open PR -> `In Progress`;
- merged or closed PR -> `Done`.

Do not create a second prose lifecycle when these facts are mechanically available.

## Issues and discoveries

Use issues and pull requests together. An issue owns a reviewable capability, defect, decision, or other durable work node; a pull request owns one concrete reviewable change and links to its owning issue using GitHub Development/closing semantics when targeting the default branch.

When work exposes another independently reviewable concern, reuse an existing owning issue if one exists; otherwise create a new issue and connect it with native hierarchy or dependency relations where applicable. Material discovery issues carry the non-exclusive `discovery` tag so automation can scope Project-field reconciliation; the typed `Discovery disposition` Project field remains authoritative. Do not expand a pull request merely to absorb unrelated discoveries.

## Pull-request discipline

- One primary conceptual purpose per pull request.
- Prefer small topologically ordered or stacked pull requests over broad changes.
- Tests required to prove a change belong in the same pull request.
- Target at most 400 changed lines. From 401-700 lines, actively consider splitting; above 700 lines, split unless atomicity is explicitly justified.
- Intermediate stacked pull requests may target a non-default parent branch. When retargeted to `main`, establish the required native closing/Development ownership before merge.
- Every open non-draft pull request targeting `main` must receive a Codex review before merge.
- Codex review freshness is SHA-bound. Finding-bearing reviews use native `PullRequestReview.commit_id == head.sha`; clean reviews emitted by the Codex GitHub App as bot comments must explicitly name the current reviewed commit. If the head moves, invoke `@codex review` again and reconcile every material finding before merge.
- Never mutate `main` directly during ordinary work.

### Freeze, validation, review, and merge sequencing

Exact-head evidence is useful only when the order of operations is disciplined. Do not turn freshness checks into a loop of overlapping CI runs and stale reviews.

Use this sequence for every merge candidate:

1. **Complete the mutable work first.** Finish implementation, tests, controlled-document reconciliation, issue/discovery ownership, scope review, and any other known canonicality audit before declaring a candidate head.
2. **Freeze one candidate head SHA.** Once frozen, do not push opportunistic cleanup while deterministic checks or Codex review are in flight.
3. **Run the deterministic checks applicable to the change class.** Fix deterministic failures before requesting the final Codex review. A fix creates a new candidate head and restarts this sequence.
4. **Request exactly one Codex review for the frozen head after applicable deterministic checks are green.** Do not queue another `@codex review` while an earlier request for the same PR is still being processed.
5. **If the head moved while an older Codex review was in flight, treat that review as stale.** Let the existing request finish or otherwise become settled before requesting exactly one fresh review for the new frozen head. Never create a procession of concurrent review requests for successive SHAs.
6. **Reconcile findings and review threads.** A material finding becomes a GitHub discovery when required. If correcting it changes source, return to step 2 with the new head.
7. **Merge only the reviewed frozen head.** Recheck mergeability, applicable green checks, unresolved review threads, work-graph ownership/disposition, and exact-head Codex evidence, then merge with `expected_head_sha`.

Evidence invalidation is change-class aware:

- application code, tests, build/deployment configuration, or CI/workflow source changes require fresh applicable executable CI on the resulting head;
- documentation-only source changes require their applicable documentation/consistency checks but do **not** automatically require an expensive application CI replay when executable/package behavior and the validation workflow are unchanged;
- any source commit, including documentation, moves the Git head and therefore makes a prior Codex review stale for merge;
- PR body edits, comments, issue fields/relations, Project fields, and other GitHub metadata do not move the Git head and therefore do not invalidate SHA-bound CI or Codex evidence;
- when a documentation-only commit follows a green executable commit, record that relationship explicitly rather than pretending an older executable artifact was produced from the newer source SHA.

The final canonicality/scope audit belongs **before** the freeze and final review. Do not conduct a broad new audit after Codex has approved a merge candidate unless a concrete new signal requires it.

## Specification-driven development

The consumer application owns its problem statement, SRS, functional and non-functional requirements, assumptions, acceptance criteria, SDD, ADRs, UML sources, tests, implementation, and evidence. Implementation proceeds through progressively real vertical slices rather than a document phase followed by a code phase.

PlantUML diagrams are first-class parts of the SRS/SDD when they carry system semantics. Rendered SVG/HTML/PDF files are generated views, not independent authorities.

## Architecture and reuse

Prefer composition to inheritance. Preserve strict hexagonal dependency direction: domain/application semantics depend on project-owned interfaces and ports, while frameworks, persistence, AI providers, vector stores, deployment products, and other external technologies remain adapters or infrastructure.

Minimize project-owned code. Search in this order before implementing non-trivial generic capability:

1. standard library or platform-native capability;
2. an already-approved repository dependency;
3. a mature maintained external component;
4. custom implementation only for a demonstrated residual gap.

External types must not leak into durable domain/application contracts merely because a framework is convenient.

## Stubs and iterative delivery

A stub is a replaceable adapter behind the same production boundary, not a throwaway parallel architecture. Build a deployable hollow mock-up early, then replace stub adapters incrementally with production-like implementations while keeping the shell, ports, topology, tests, and deployment path stable.

Each meaningful increment must remain demonstrable and deployable. Deployment and review are continuous engineering feedback, not end-of-project phases.

## Verification and review

Mechanically decidable properties belong in deterministic checks. AI review focuses on ambiguity, architecture, counterexamples, failure paths, missing assumptions, and verification gaps rather than formatting or lint already owned by tooling.

Before merge, verify the exact PR head, applicable checks, unresolved review threads, work-graph metadata, scope size, current-head Codex review evidence, and the evidence required by the owning issue.
