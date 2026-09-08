# Repository metrics

> Generated file. Do not edit the figures by hand. Run `bash scripts/repository-metrics.sh generate` from a clean checkout on a Docker host.

The table reports physical blank, comment and code lines. The README total and per-language badges are generated from this single cloc result, in cloc order. Production code, tests, authored documentation, workflow/configuration and database/diagram sources are combined; those categories are not presented as separate totals.

Only Git-tracked files are candidates. The generator excludes its own report, dependency lock files, binary/rendered assets, and generated, vendored or private paths. It counts a temporary README copy with the managed metrics-badge body removed, so the badges cannot count themselves.

The counting image is pinned as `aldanial/cloc:2.08@sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3`; its bundled binary identifies itself in the table below. It runs with networking disabled and the prepared source snapshot mounted read-only.

The `source-reference` workflow verifies this committed report on an exact checkout. Its downloadable artifact carries a copy of the report with the exact source SHA, workflow run and attempt appended; release evidence must cite that attested copy rather than infer a source revision from these static figures.

cloc|github.com/AlDanial/cloc v 2.04
--- | ---

Language|files|blank|comment|code
:-------|-------:|-------:|-------:|-------:
Java|151|1160|813|9362
Python|17|767|622|6699
YAML|28|305|51|4201
Markdown|43|1488|2|2956
TypeScript|14|229|316|2343
Bourne Shell|23|230|63|2236
PlantUML|36|246|0|2025
JavaScript|2|70|2|1758
Maven|1|0|0|205
SQL|6|3|11|159
JSON|9|0|0|155
Dockerfile|3|8|14|70
Text|3|11|0|68
Properties|3|0|3|39
HTML|1|0|0|5
--------|--------|--------|--------|--------
SUM:|340|4517|1897|32281
