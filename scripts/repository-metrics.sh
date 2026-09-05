#!/usr/bin/env bash
# Recomputes reviewer telemetry from tracked files and rejects README metrics that no longer match.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${ROOT}/docs/reviewer/repository-metrics.md"
README="${ROOT}/README.md"
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
      if (path ~ /\.(svg|png|jpe?g|gif|webp|ico|pdf|zip|jar|pb)$/) next
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

> Generated file. Do not edit the figures by hand. Run `bash scripts/repository-metrics.sh generate` from a clean checkout on a Docker host.

The table reports physical blank, comment and code lines using the pinned counting image below. It combines production code, tests, authored documentation, workflow/configuration and database/diagram sources; it does not present those categories as separate totals.

Only Git-tracked files are candidates. The generator excludes its own report, dependency lock files, binary/rendered assets, and any path component named `target`, `node_modules`, `dist`, `playwright-report`, `test-results`, `.checkpoints`, `.worktrees`, `graphify-out`, `generated-diagrams`, `vendor` or `coverage`.

The counting image is pinned by digest as `aldanial/cloc:2.08@sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3`; its bundled binary declares itself as `github.com/AlDanial/cloc v 2.04`. It runs with networking disabled and the checkout mounted read-only.

EOF
    cat "${temporary}/cloc.md"
  } > "${destination}"
}

report_code_lines() {
  awk -F'|' '$1 == "SUM:" { gsub(/[[:space:]]/, "", $5); print $5 }' "$1"
}

expected_badge() {
  local code_lines="$1"
  local encoded="${code_lines}"
  if [[ "${#code_lines}" -gt 3 ]]; then
    encoded="${code_lines:0:${#code_lines}-3}%2C${code_lines: -3}"
  fi
  printf '[![Authored LOC](https://img.shields.io/badge/authored_LOC-%s-informational)](docs/reviewer/repository-metrics.md)' "${encoded}"
}

update_readme_badge() {
  local badge="$1"
  local temporary
  temporary="$(mktemp)"
  awk -v badge="${badge}" '
    $0 == "<!-- repository-metrics-badge:start -->" { print; print badge; replacing = 1; next }
    $0 == "<!-- repository-metrics-badge:end -->" { replacing = 0; print; next }
    !replacing { print }
  ' "${README}" > "${temporary}"
  mv "${temporary}" "${README}"
}

mode="${1:-}"
case "${mode}" in
  generate)
    generate_report "${OUTPUT}"
    code_lines="$(report_code_lines "${OUTPUT}")"
    [[ "${code_lines}" =~ ^[0-9]+$ ]] || { echo "Unable to read cloc SUM code lines." >&2; exit 1; }
    update_readme_badge "$(expected_badge "${code_lines}")"
    echo "Updated ${OUTPUT#"${ROOT}/"} and README.md (${code_lines} authored LOC)."
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
    code_lines="$(report_code_lines "${candidate}")"
    badge="$(expected_badge "${code_lines}")"
    if ! grep -Fqx "${badge}" "${README}"; then
      echo "README authored-LOC badge is stale. Regenerate it with:" >&2
      echo "  bash scripts/repository-metrics.sh generate" >&2
      exit 1
    fi
    echo "Repository metrics and README badge are current (${code_lines} authored LOC)."
    ;;
  *)
    usage
    exit 2
    ;;
esac
