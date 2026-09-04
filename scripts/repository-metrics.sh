#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${ROOT}/docs/reviewer/repository-metrics.md"
CLOC_IMAGE="aldanial/cloc:2.08@sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3"

usage() {
  echo "Usage: $0 generate|check" >&2
}

tracked_sources() {
  git -C "${ROOT}" ls-files | awk '
    {
      path = tolower($0)
      if (path == "docs/reviewer/repository-metrics.md") next
      if (path ~ /(^|\/)package-lock\.json$/) next
      if (path ~ /(^|\/)(target|node_modules|dist|playwright-report|test-results|\.checkpoints|\.worktrees|graphify-out|generated-diagrams|vendor|coverage)(\/|$)/) next
      if (path ~ /\.(svg|png|jpe?g|gif|webp|ico|pdf|zip|jar)$/) next
      print
    }
  ' | LC_ALL=C sort
}

generate_report() {
  local destination="$1"
  local temporary
  temporary="$(mktemp -d)"
  trap 'rm -rf "${temporary}"' RETURN

  tracked_sources > "${temporary}/files.txt"
  if [[ ! -s "${temporary}/files.txt" ]]; then
    echo "No version-controlled source files remain after repository-metric exclusions." >&2
    return 1
  fi

  docker run --rm -i \
    --network none \
    --read-only \
    --security-opt no-new-privileges \
    --tmpfs /tmp:rw,noexec,nosuid,size=16m \
    --user "$(id -u):$(id -g)" \
    -v "${ROOT}:/workspace:ro" \
    -w /workspace \
    "${CLOC_IMAGE}" \
    --md --quiet --hide-rate --unix --list-file=- \
    < "${temporary}/files.txt" > "${temporary}/cloc.md"

  {
    cat <<'EOF'
# Repository metrics

> Generated file. Do not edit the figures by hand. Run `bash scripts/repository-metrics.sh generate` from a clean checkout.

The table reports physical blank, comment and code lines using the bundled binary that declares itself as `cloc` v2.04. It combines production code, tests, authored documentation, workflow/configuration and database/diagram sources; it does not present those categories as separate totals.

Only Git-tracked files are candidates. The generator excludes its own report, dependency lock files, binary/rendered assets, and any path component named `target`, `node_modules`, `dist`, `playwright-report`, `test-results`, `.checkpoints`, `.worktrees`, `graphify-out`, `generated-diagrams`, `vendor` or `coverage`.

The counting image is referenced by tag and digest as `aldanial/cloc:2.08@sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3`; its bundled binary declares itself as `github.com/AlDanial/cloc v 2.04` in the generated table. It runs with networking disabled and the checkout mounted read-only.

EOF
    cat "${temporary}/cloc.md"
  } > "${destination}"
}

mode="${1:-}"
case "${mode}" in
  generate)
    generate_report "${OUTPUT}"
    echo "Updated ${OUTPUT#"${ROOT}/"}"
    ;;
  check)
    candidate="$(mktemp)"
    trap 'rm -f "${candidate}"' EXIT
    generate_report "${candidate}"
    if ! cmp -s "${OUTPUT}" "${candidate}"; then
      echo "Committed repository metrics are stale. Regenerate them with:" >&2
      echo "  bash scripts/repository-metrics.sh generate" >&2
      diff -u "${OUTPUT}" "${candidate}" || true
      exit 1
    fi
    echo "Repository metrics are current."
    ;;
  *)
    usage
    exit 2
    ;;
esac
