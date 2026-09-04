# Repository metrics

> Generated file. Do not edit the figures by hand. Run `bash scripts/repository-metrics.sh generate` from a clean checkout.

The table reports physical blank, comment and code lines using `cloc` 2.08. It combines production code, tests, authored documentation, workflow/configuration and database/diagram sources; it does not present those categories as separate totals.

Only Git-tracked files are candidates. The generator excludes its own report, dependency lock files, binary/rendered assets, and any path component named `target`, `node_modules`, `dist`, `playwright-report`, `test-results`, `.checkpoints`, `.worktrees`, `graphify-out`, `generated-diagrams`, `vendor` or `coverage`.

The counting runtime is the pinned `aldanial/cloc:2.08` container at digest `sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3` with networking disabled and the checkout mounted read-only.

cloc|github.com/AlDanial/cloc v 2.08
--- | ---

Language|files|blank|comment|code
:-------|-------:|-------:|-------:|-------:
Java|103|754|78|5840
YAML|20|257|20|3397
Markdown|25|1173|0|2237
PlantUML|30|217|0|1784
TypeScript|11|147|0|1398
Python|3|149|17|906
Bourne Shell|15|116|27|712
JavaScript|1|28|0|344
SQL|5|3|5|149
Maven|1|0|0|132
JSON|4|0|0|64
Dockerfile|2|6|8|43
Text|2|0|0|15
HTML|1|0|0|5
--------|--------|--------|--------|--------
SUM:|223|2850|155|17026
