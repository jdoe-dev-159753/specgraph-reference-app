# Source-derived reference documentation

The reviewer reference is a generated view of maintained source documentation. It helps navigate implementation and HTTP surfaces; it does not replace the SRS, SDD, ADRs, tests, or source contracts.

## Generate the reference

From the repository root, generate the complete bundle:

```bash
./scripts/generate-source-reference.sh
```

The bundle is written beneath `backend/target/source-reference/`. Its root index links to Java implementation documentation, the frontend/browser implementation reference, and the HTTP contract reference. The component renderers remain directly callable as `./scripts/render-frontend-reference.sh [output-directory]` and `./scripts/render-openapi-reference.sh [output-directory]`.

The renderer is Redocly CLI pinned by version and multi-platform image digest. It runs without network access and reads only the repository-owned `backend/src/main/resources/static/openapi.yaml`. Generated HTML stays under the ignored `backend/target/` tree and is not committed.

## Authority boundaries

| Surface | Maintained authority | Generated view |
| --- | --- | --- |
| HTTP endpoints and schemas | `backend/src/main/resources/static/openapi.yaml` | `http-api/index.html` |
| Java contracts and module boundaries | Javadoc and `package-info.java` in production sources | Maven Javadoc output |
| Browser implementation and executable scenarios | TSDoc in `frontend/**/*.ts(x)` and `e2e/**/*.ts`, plus the complete source inventory | TypeDoc frontend reference |
| Requirements and architecture | SRS, SDD, ADRs, and their controlled diagram sources | None; links remain links to the authorities |

The `source-reference` workflow assembles the HTTP and Java views into one downloadable `source-reference-<source SHA>` artifact. A clean generation must preserve the existing OpenAPI contract checks; the renderer never derives or rewrites that contract.

## Frontend and browser implementation reference

The frontend is an application rather than a reusable library, but its implementation intent remains reviewer-relevant. The generated TypeDoc site therefore uses expanded file entry points and documented exports to make application components, HTTP shapes, formatting/query functions, browser-test fixtures, and configuration factories navigable. Exporting a symbol for documentation does **not** declare it a stable external API; `openapi.yaml` remains the only HTTP authority.

The source commentary covers:

- the session state machine, CSRF ownership, protected-content gate, and logout cache clearing;
- draft-versus-submitted customer query state, activity/history pagination, and duplicate-submission guards;
- separation of source risk, detector artifacts, policy grounding, and model execution provenance;
- every Playwright scenario's measured behavior and explicit evidence limits;
- the single application mount point, shared query cache, visual evidence policy, Vite same-origin proxy, compiler configurations, and browser-tool dependencies.

`docs/reviewer/frontend-source-inventory.json` inventories 17 maintained browser files: 12 TypeScript/TSX sources and five HTML/JSON configuration authorities. `python3 scripts/check-frontend-source-reference.py` fails when a file is added or removed without reconciliation, when a TypeScript module lacks module intent, or when a type, function, exported variable, hook/state declaration, named arrow function, or Playwright scenario lacks a preceding documentation comment.

Configuration files that cannot carry TSDoc remain intentional:

| File | Decision represented |
| --- | --- |
| `frontend/index.html` | Owns one stateless application mount point; session and business state stay in React/server boundaries. |
| `frontend/package.json` | Declares the ADR-004 React/MUI/TanStack/Vite assembly; it is an application package, not a published library. |
| `frontend/tsconfig.json` | Enforces strict, no-emit, bundler-resolved browser compilation; Vite alone owns asset emission. |
| `frontend/tsconfig.node.json` | Isolates the composite build-tool configuration from browser DOM compilation. |
| `e2e/package.json` | Pins Playwright exactly so recorded browser evidence does not silently adopt a newer test runtime. |

TypeDoc 0.28.20 currently consumes the TypeScript compiler API through 6.x, while the application build uses TypeScript 7. The documentation toolchain is consequently isolated under `docs/tooling/frontend-reference/` with its own locked TypeScript 6.0.2 parser and `skipErrorChecking`; the normal frontend `tsc --noEmit` build remains the type-correctness authority. TypeDoc produces navigation and renders comments, while the source inventory ratchet owns completeness.

## Reviewer use

Download the `source-reference-<source SHA>` artifact from the corresponding GitHub Actions run, extract it, and open its root `index.html` to navigate to the HTTP and Java views. Because generated files are views, review disagreements are resolved against their maintained source rather than by editing generated HTML.
