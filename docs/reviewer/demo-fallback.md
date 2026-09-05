# Demo fallback evidence

A live demo remains the preferred reviewer path. If it is unavailable, use the retained Playwright screenshots together with `docs/reviewer/screenshot-manifest.md`. The manifest must bind the evidence to its workflow artifact and source SHA. This screenshot-and-provenance package is the mandatory fallback.

Follow the same sequence as the live path:

1. R0 -> R3 progression on ports 8080-8083;
2. R4 baseline on 8084;
3. R4 Bayesian on 8085;
4. show policy retrieval provenance and Bayesian detector provenance;
5. if available, show optional local Stage-3 inference on 8086;
6. finish on the controlled architecture figures and GitHub delivery evidence.

A short recorded walkthrough may supplement the screenshots but is optional. Any recording must disclose its source SHA and date, and must not be presented as current runtime state after that revision becomes stale.
