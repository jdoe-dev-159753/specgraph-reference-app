# Repository metrics

> Generated file. Do not edit the figures by hand. Run `bash scripts/repository-metrics.sh generate` from a clean checkout on a Docker host.

The table reports physical blank, comment and code lines using the pinned counting image below. It combines production code, tests, authored documentation, workflow/configuration and database/diagram sources; it does not present those categories as separate totals.

Only Git-tracked files are candidates. The generator excludes its own report, dependency lock files, binary/rendered assets, and any path component named `target`, `node_modules`, `dist`, `playwright-report`, `test-results`, `.checkpoints`, `.worktrees`, `graphify-out`, `generated-diagrams`, `vendor` or `coverage`.

The counting image is pinned by digest as `aldanial/cloc:2.08@sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3`; its bundled binary declares itself as `github.com/AlDanial/cloc v 2.04`. It runs with networking disabled and the checkout mounted read-only.

cloc|github.com/AlDanial/cloc v 2.04
--- | ---

Language|files|blank|comment|code
:-------|-------:|-------:|-------:|-------:
Java|126|1084|93|8467
YAML|21|262|22|3524
Markdown|31|1331|2|2549
PlantUML|36|246|0|2025
TypeScript|12|194|0|1796
Python|5|194|78|1094
Bourne Shell|15|124|27|830
JavaScript|1|28|0|344
SQL|5|3|5|149
Maven|1|0|0|138
Dockerfile|3|9|9|66
JSON|4|0|0|64
Properties|2|0|0|34
Text|2|0|0|15
HTML|1|0|0|5
--------|--------|--------|--------|--------
SUM:|265|3475|236|21100
