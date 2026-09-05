# Source-derived reference documentation

The reviewer reference is a generated view of maintained source documentation. It helps navigate implementation and HTTP surfaces; it does not replace the SRS, SDD, ADRs, tests, or source contracts.

## Generate the reference

From the repository root, run:

```bash
./scripts/render-openapi-reference.sh
```

The command writes the browsable HTTP reference to `backend/target/source-reference/http-api/index.html`. A different repository-relative output directory can be supplied as the first argument; the generated file inside it remains `index.html`.

The renderer is Redocly CLI pinned by version and multi-platform image digest. It runs without network access and reads only the repository-owned `backend/src/main/resources/static/openapi.yaml`. Generated HTML stays under the ignored `backend/target/` tree and is not committed.

## Authority boundaries

| Surface | Maintained authority | Generated view |
| --- | --- | --- |
| HTTP endpoints and schemas | `backend/src/main/resources/static/openapi.yaml` | `http-api/index.html` |
| Java contracts and module boundaries | Javadoc and `package-info.java` in production sources | Maven Javadoc output |
| Requirements and architecture | SRS, SDD, ADRs, and their controlled diagram sources | None; links remain links to the authorities |

The `source-reference` workflow assembles the HTTP and Java views into one downloadable `source-reference-<source SHA>` artifact. A clean generation must preserve the existing OpenAPI contract checks; the renderer never derives or rewrites that contract.

## Frontend TypeDoc applicability

TypeDoc is intentionally not part of this reference. The current frontend is an application, not a reusable TypeScript library:

- `frontend/src/main.tsx` is the browser composition root and exports nothing;
- `frontend/src/App.tsx` exports only the root React component used by that composition root;
- `frontend/vite.config.ts` exports build-tool configuration rather than an application contract.

Generating TypeDoc for those entry points would create an effectively empty reference and add a dependency without documenting a durable public surface. Revisit this decision if the frontend introduces reusable exported API clients, shared domain types, hooks, or component contracts consumed outside their defining module.

## Reviewer use

Download the `source-reference-<source SHA>` artifact from the corresponding GitHub Actions run, extract it, and open its root `index.html` to navigate to the HTTP and Java views. Because generated files are views, review disagreements are resolved against their maintained source rather than by editing generated HTML.
