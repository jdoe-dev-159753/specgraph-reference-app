# Agent and engineering governance

## Authority

Specifications, design decisions, tests, source, Git history, GitHub metadata, reviews, and execution
evidence are authoritative according to their role. Agent output is a proposal or review aid.

## Work graph

Use native GitHub issues, pull requests, parent/sub-issues, dependencies, duplicates, assignees,
milestones, close reasons, and Development links. Free text explains intent and evidence; it does not
duplicate lifecycle or hierarchy.

Delivery priority is an independent planning field with `MANDATORY`, `MUST_HAVE`, and `NICE_TO_HAVE`.
Only root planning issues own an explicit value. Descendants inherit recursively. A pull request derives
the highest urgency from its complete native owner-issue set.

Discovery disposition is used only on issues tagged `discovery`: `IN_SCOPE`, `FOLLOW_UP`,
`ALREADY_TRACKED`, or `NON_ACTIONABLE`. Each value must agree with native closing, duplicate, or close
reason facts.

## Delivery

- Keep one conceptual purpose per pull request and include the tests that prove it.
- Prefer composition and project-owned ports; infrastructure and provider types remain adapters.
- Reuse the platform, an approved dependency, or a mature maintained component before custom code.
- Complete mutations and audits, freeze one candidate SHA, run applicable deterministic checks, then
  request exactly one fresh review for that head.
- A changed head invalidates prior review evidence. Merge only the reviewed frozen head.
- Generated views are disposable projections; their sources remain authoritative.

## Specification ownership

The consumer repository owns its problem, requirements, invariants, acceptance criteria, design, ADRs,
tests, implementation, deployment, and evidence. Deliver progressively real vertical slices rather
than a document phase followed by an implementation phase.
