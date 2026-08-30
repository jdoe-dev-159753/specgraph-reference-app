# AGENTS.md

These instructions apply repository-wide unless a deeper `AGENTS.md` narrows them.

## Engineering authority

Specifications, design decisions, tests, code, Git history, GitHub metadata, review evidence, and execution evidence are authoritative according to their role. AI output is a proposal or review aid, never a substitute source of truth.

## GitHub-native work graph

GitHub owns mechanically representable work state. Use native issue state and close reasons, assignees, milestones, issue types, Projects fields, parent/sub-issues, blocked-by/blocking dependencies, duplicate relations, and Development/closing PR links whenever those semantics exist.

Do not encode lifecycle, hierarchy, dependency, duplicate, implementation ownership, or exclusive workflow state as prose fields, title prefixes, checkboxes, comments, or labels. Labels are non-exclusive semantic tags such as `architecture`, `backend`, `frontend`, `database`, `security`, `ai`, `rag`, `deployment`, `testing`, `documentation`, and `discovery`; they never emulate an enum.

A custom Project field is justified only for a genuine independent closed dimension that GitHub does not represent natively, such as delivery priority or discovery disposition. Use a single-select field rather than prose or mutually-exclusive labels.

Free text explains purpose, scope, evidence, rationale, assumptions, acceptance criteria, non-goals, and reviewability. It does not duplicate the work graph.

## Issues and discoveries

Use issues and pull requests together. An issue owns a reviewable capability, defect, decision, or other durable work node; a pull request owns one concrete reviewable change and links to its owning issue using GitHub Development/closing semantics when targeting the default branch.

When work exposes another independently reviewable concern, reuse an existing owning issue if one exists; otherwise create a new issue and connect it with native hierarchy or dependency relations where applicable. Material discovery issues carry the non-exclusive `discovery` tag so automation can scope Project-field reconciliation; the typed `Discovery disposition` Project field remains authoritative. Do not expand a pull request merely to absorb unrelated discoveries.

## Pull-request discipline

- One primary conceptual purpose per pull request.
- Prefer small topologically ordered or stacked pull requests over broad changes.
- Tests required to prove a change belong in the same pull request.
- Target at most 400 changed lines. From 401-700 lines, actively consider splitting; above 700 lines, split unless atomicity is explicitly justified.
- Intermediate stacked pull requests may target a non-default parent branch. When retargeted to `main`, establish the required native closing/Development ownership before merge.
- Never mutate `main` directly during ordinary work.

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

Before merge, verify the exact PR head, applicable checks, unresolved review threads, work-graph metadata, scope size, and the evidence required by the owning issue.
