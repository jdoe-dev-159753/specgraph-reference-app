# Reviewer visual evidence

This directory is the stable repository home for reviewer-facing visual evidence that is safe to show outside CI logs.

## Controlled architecture figures

The canonical sources remain under `docs/assignment/SDD/diagrams/`. The root README embeds selected rendered SVGs directly from that controlled directory rather than copying them here.

## Application screenshots

Authentic browser screenshots are produced by the repository-owned Playwright/CI flows. When a screenshot is promoted into the repository it must retain:

- the exact source SHA or immutable checkpoint identity;
- the ring / runtime variant that produced it;
- the customer/scenario identity;
- the workflow/run provenance from which it was captured;
- no secret or real customer data.

Do not add mockups or manually reconstructed screenshots to satisfy this section. Existing CI screenshots for R1/R2/R3/R4 are the source material for the first promotion pass owned by #145.

## Demo fallback video

A short recorded walkthrough may be retained here (or linked from a durable release artifact) as a fallback for a failed live demonstration. It must show the same executable rings/configurations documented in the root README and must not be presented as fresher than the revision it actually records.
