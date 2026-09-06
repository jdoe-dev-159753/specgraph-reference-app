# Governed project bootstrap template

This directory is the deliberately small reusable residue of the completed reference application.
It is a starting point for a new repository, not a framework and not a clone of project history.

## What is reusable

- `AGENTS.md`: evidence authority, specification ownership, hexagonal boundaries, and exact-head review discipline;
- `controlled/`: a concise Inception plus one machine-readable requirements, design, ADR, and verification chain;
- `github-project.md`: native GitHub work-graph and the two justified independent Project fields;
- `self-hosted-ci.md`: runner labels, confinement, cleanup, and exact-source verification;
- `reuse-checklist.md`: a short removal/adoption gate before the template becomes a real project.

## What is intentionally absent

There are no domain requirements, employer names, delivery rings, milestone dates, repository-specific
issue numbers, screenshots, runtime credentials, runner registrations, or historical evidence. A new
project must write those facts from its own problem and record actual execution evidence in its own
GitHub history.

## Bootstrap

1. Copy the files into a new repository and rename the example identifiers before the first review.
2. Replace the Inception assumptions, then write the requirements and acceptance criteria.
3. Add only design identities and ADRs that refine those requirements, then connect verification obligations.
4. Create one root planning issue per durable capability and represent hierarchy/dependencies natively.
5. Decide the root issues' delivery priority; descendants inherit it from their native parent chain.
6. Register a confined self-hosted runner only if the workload genuinely requires it.
7. Add deterministic validation for the chosen stack, freeze one candidate SHA, then obtain one fresh
   exact-head review before merge.

Generated documentation and evidence views may be added later. They never become a competing source
of requirements, design, lifecycle, or pass/fail truth.
