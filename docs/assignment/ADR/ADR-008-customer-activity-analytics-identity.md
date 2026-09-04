# ADR-008 — Present the product as Customer Activity Analytics

**Decision date:** 2026-09-04

**Decision owner:** final reviewer-facing delivery

**Normative inputs:** issue #178, J5 final freeze, `ADR-005`, `ADR-006`

## Context

The delivered application is a concrete customer-care product: it lets authenticated operators review customer activity, inspect distinct source and detector evidence, retrieve applicable policy knowledge and retain analysis history. The earlier `SpecGraph` and `reference-app` names describe the repository's origin and engineering harness, not the product a reviewer is evaluating.

Removing those names from reviewer-facing product copy is a bounded documentation change. Renaming the GitHub repository or its mechanically coupled technical identifiers is not. Repository URLs, workflow badges, self-hosted runner registrations, GHCR packages, Compose identities, Java packages, configuration keys, CI references and retained checkpoint evidence all depend on those identifiers. Changing only some of them during the final freeze would create broken links or ambiguous provenance; changing all of them would be an integration migration that requires its own verification cycle.

Historical issues, pull requests, commits, release evidence and checkpoint instructions must also remain reproducible. Their original identifiers are evidence, not presentation defects.

## Decision

The public product identity is **Customer Activity Analytics**.

Reviewer-facing headings and descriptive copy use that name and describe the concrete application directly. `SpecGraph`, `specgraph` and `Reference App` remain only where they are required as existing repository, runtime, package, configuration, infrastructure or historical evidence identifiers.

There is an explicit **no-go** on renaming the GitHub repository or mechanically coupled technical identifiers during the J5 final freeze. This decision rejects both a repository rename and a partial text replacement for the final delivery. The existing repository path, badges, workflow references, runner registrations, GHCR coordinates, Compose identities, Java namespace, configuration keys and historical evidence remain stable.

Any later technical-identity migration is a separate integration change. It must enumerate every coupled identifier, preserve or redirect public entry points, migrate publication and runner configuration atomically, and verify clone, build, deployment, package publication and archived evidence before replacing the current identities.

## Consequences

- the repository presents one neutral, domain-specific product name to reviewers;
- the final-freeze change is limited to presentation and does not invalidate executable behavior or deployment evidence;
- technical `specgraph` occurrences are not removed merely for cosmetic uniformity;
- existing URLs, automation, container coordinates and historical provenance remain valid;
- a future repository or technical-identifier rename cannot be inferred from this ADR and requires an independently owned, mechanically verified migration.

## Verification consequences

- the root README heading and opening descriptor identify Customer Activity Analytics without harness/bootstrap branding;
- reviewer-facing documentation may retain `specgraph` only when it names a concrete compatibility, infrastructure or historical identifier;
- final demo and release checks continue to use the existing repository, workflow, runner, registry and Compose coordinates;
- no source package, configuration key, workflow, image coordinate, runner label or generated historical evidence is renamed by this decision.
