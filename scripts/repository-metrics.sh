#!/usr/bin/env bash
# Recomputes reviewer telemetry from one cloc result and rejects stale reports or badges.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${ROOT}/docs/reviewer/repository-metrics.md"
README="${ROOT}/README.md"
CLOC_IMAGE="aldanial/cloc:2.08@sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3"
BADGE_START='<!-- repository-metrics-badge:start -->'
BADGE_END='<!-- repository-metrics-badge:end -->'
TEST_MODE_MARKER='> TEST MODE OUTPUT. This report is not release evidence.'

usage() {
  echo "Usage: $0 generate|check" >&2
}

tracked_sources() {
  git -C "${ROOT}" ls-files | awk '
    {
      path = tolower($0)
      if (path == "docs/reviewer/repository-metrics.md") next
      if (path ~ /^docs\/presentation\/output\//) next
      if (path ~ /(^|\/)(package-lock\.json|npm-shrinkwrap\.json|pnpm-lock\.yaml|yarn\.lock|bun\.lockb?|cargo\.lock|poetry\.lock|pipfile\.lock|composer\.lock|gemfile\.lock)$/) next
      if (path ~ /\.(lock|lockb)$/) next
      if (path ~ /(^|\/)(target|node_modules|dist|build|coverage|playwright-report|test-results|\.checkpoints|\.worktrees|graphify-out|generated|generated-diagrams|vendor|private|\.private)(\/|$)/) next
      if (path ~ /\.(svg|png|jpe?g|gif|webp|ico|pdf|zip|jar|pb|pptx?|docx?|xlsx?)$/) next
      print
    }
  ' | LC_ALL=C sort
}

require_clean_source_checkout() {
  local temporary="$1"
  local pathspecs=(
    .
    ':(exclude)README.md'
    ':(exclude)docs/reviewer/repository-metrics.md'
  )

  if ! git -C "${ROOT}" diff --quiet -- "${pathspecs[@]}" ||
     ! git -C "${ROOT}" diff --cached --quiet -- "${pathspecs[@]}"; then
    echo "Repository metrics require a clean tracked source checkout." >&2
    return 1
  fi

  git -C "${ROOT}" show HEAD:README.md > "${temporary}/head-readme.md"
  strip_badge_body "${temporary}/head-readme.md" > "${temporary}/head-readme-sanitized.md"
  strip_badge_body "${README}" > "${temporary}/worktree-readme-sanitized.md"
  if ! cmp -s "${temporary}/head-readme-sanitized.md" "${temporary}/worktree-readme-sanitized.md"; then
    echo "README changes outside the generated metrics block must be committed first." >&2
    return 1
  fi
}

strip_badge_body() {
  awk -v start="${BADGE_START}" -v end="${BADGE_END}" '
    $0 == start {
      starts += 1
      if (starts > 1 || replacing) exit 40
      print
      replacing = 1
      next
    }
    $0 == end {
      ends += 1
      if (!replacing || ends > 1) exit 41
      replacing = 0
      print
      next
    }
    !replacing { print }
    END {
      if (starts != 1 || ends != 1 || replacing) exit 42
    }
  ' "$1"
}

prepare_snapshot() {
  local snapshot="$1"
  local files="$2"
  mkdir -p "${snapshot}"

  (cd "${ROOT}" && tar -cf - -T "${files}") | tar -xf - -C "${snapshot}"

  [[ -f "${snapshot}/README.md" ]] || {
    echo "README.md is not present in the tracked metric source set." >&2
    return 1
  }
  strip_badge_body "${snapshot}/README.md" > "${snapshot}/README.md.sanitized"
  mv "${snapshot}/README.md.sanitized" "${snapshot}/README.md"
}

run_cloc() {
  local snapshot="$1"
  local files="$2"
  local destination="$3"
  local override="${REPOSITORY_METRICS_CLOC_BIN:-}"

  if [[ -n "${override}" ]]; then
    if [[ "${override}" != /* ]]; then
      override="$(cd "${ROOT}/$(dirname "${override}")" && pwd)/$(basename "${override}")"
    fi
    (
      cd "${snapshot}"
      "${override}" --md --quiet --hide-rate --unix --list-file=- < "${files}"
    ) | tr -d '\r' > "${destination}"
    return
  fi

  docker run --rm -i \
    --network none \
    --read-only \
    --security-opt no-new-privileges \
    --tmpfs /tmp:rw,noexec,nosuid,size=16m \
    --user "$(id -u):$(id -g)" \
    -v "${snapshot}:/workspace:ro" \
    -w /workspace \
    "${CLOC_IMAGE}" \
    --md --quiet --hide-rate --unix --list-file=- \
    < "${files}" | tr -d '\r' > "${destination}"
}

parse_cloc_rows() {
  awk -F'|' '
    function trim(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      return value
    }
    {
      language = trim($1)
      if (language == "Language") {
        headers += 1
        table = 1
        next
      }
      if (!table || language == "" || language ~ /^-+$/ || language ~ /^:-+$/) next
      files = trim($2)
      blank = trim($3)
      comment = trim($4)
      code = trim($5)
      if (NF != 5 || files !~ /^[0-9]+$/ || blank !~ /^[0-9]+$/ ||
          comment !~ /^[0-9]+$/ || code !~ /^[0-9]+$/) {
        malformed = 1
        next
      }
      if (language == "SUM:") {
        print "__TOTAL__\t" code
        totals += 1
        total = code + 0
        finished = 1
        next
      }
      if (finished || seen[language]++) {
        malformed = 1
        next
      }
      print language "\t" code
      languages += 1
      language_sum += code
    }
    END {
      if (headers != 1 || totals != 1 || languages == 0 || malformed ||
          language_sum != total) exit 43
    }
  ' "$1"
}

group_digits() {
  local remaining="$1"
  local grouped=""
  while [[ "${#remaining}" -gt 3 ]]; do
    grouped=",${remaining: -3}${grouped}"
    remaining="${remaining:0:${#remaining}-3}"
  done
  printf '%s%s' "${remaining}" "${grouped}"
}

shields_escape() {
  local input="$1"
  local output=""
  local character hex
  local index
  LC_ALL=C
  for ((index = 0; index < ${#input}; index += 1)); do
    character="${input:index:1}"
    case "${character}" in
      [a-zA-Z0-9.]) output+="${character}" ;;
      ' ') output+='_' ;;
      '_') output+='__' ;;
      '-') output+='--' ;;
      *)
        printf -v hex '%02X' "'${character}"
        output+="%${hex}"
        ;;
    esac
  done
  printf '%s' "${output}"
}

markdown_alt() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//]/\\]}"
  printf '%s' "${value}"
}

badge_line() {
  local label="$1"
  local code_lines="$2"
  local encoded_label encoded_value alt
  encoded_label="$(shields_escape "${label}")"
  encoded_value="$(shields_escape "$(group_digits "${code_lines}")")"
  alt="$(markdown_alt "${label}")"
  printf '[![%s](https://img.shields.io/badge/%s-%s-informational)](docs/reviewer/repository-metrics.md)\n' \
    "${alt}" "${encoded_label}" "${encoded_value}"
}

write_badges() {
  local rows="$1"
  local destination="$2"
  local total language code_lines
  total="$(awk -F '\t' '$1 == "__TOTAL__" { print $2 }' "${rows}")"
  [[ "${total}" =~ ^[0-9]+$ ]] || {
    echo "Unable to read the cloc SUM code lines." >&2
    return 1
  }

  badge_line 'Authored LOC' "${total}" > "${destination}"
  while IFS=$'\t' read -r language code_lines; do
    [[ "${language}" != '__TOTAL__' ]] || continue
    badge_line "${language} LOC" "${code_lines}" >> "${destination}"
  done < "${rows}"
}

write_report() {
  local cloc_output="$1"
  local destination="$2"
  {
    cat <<'EOF'
# Repository metrics

> Generated file. Do not edit the figures by hand. Run `bash scripts/repository-metrics.sh generate` from a clean checkout on a Docker host.

The table reports physical blank, comment and code lines. The README total and per-language badges are generated from this single cloc result, in cloc order. Production code, tests, authored documentation, workflow/configuration and database/diagram sources are combined; those categories are not presented as separate totals.

Only Git-tracked files are candidates. The generator excludes its own report, dependency lock files, binary/rendered assets, and generated, vendored or private paths. It counts a temporary README copy with the managed metrics-badge body removed, so the badges cannot count themselves.

The counting image is pinned as `aldanial/cloc:2.08@sha256:f4159515ece7b8d7c3729db25ef613b2f9c3e8c368f772ae5348bd6452bd57b3`; its bundled binary identifies itself in the table below. It runs with networking disabled and the prepared source snapshot mounted read-only.

The `source-reference` workflow verifies this committed report on an exact checkout. Its downloadable artifact carries a copy of the report with the exact source SHA, workflow run and attempt appended; release evidence must cite that attested copy rather than infer a source revision from these static figures.

EOF
    if [[ "${REPOSITORY_METRICS_TEST_MODE:-}" == '1' ]]; then
      printf '%s\n\n' "${TEST_MODE_MARKER}"
    fi
    cat "${cloc_output}"
  } > "${destination}"
}

update_readme_badges() {
  local badges="$1"
  local temporary
  temporary="$(mktemp)"
  if ! awk -v start="${BADGE_START}" -v end="${BADGE_END}" -v badges="${badges}" '
    $0 == start {
      starts += 1
      if (starts > 1 || replacing) exit 44
      print
      while ((getline line < badges) > 0) print line
      close(badges)
      replacing = 1
      next
    }
    $0 == end {
      ends += 1
      if (!replacing || ends > 1) exit 45
      replacing = 0
      print
      next
    }
    !replacing { print }
    END {
      if (starts != 1 || ends != 1 || replacing) exit 46
    }
  ' "${README}" > "${temporary}"; then
    rm -f "${temporary}"
    echo "README metrics badge markers are missing, duplicated or malformed." >&2
    return 1
  fi
  mv "${temporary}" "${README}"
}

read_readme_badges() {
  awk -v start="${BADGE_START}" -v end="${BADGE_END}" '
    $0 == start { inside = 1; next }
    $0 == end { inside = 0; next }
    inside { print }
  ' "${README}"
}

build_candidates() {
  local temporary="$1"
  tracked_sources > "${temporary}/files.txt"
  if [[ ! -s "${temporary}/files.txt" ]]; then
    echo "No version-controlled source files remain after repository-metric exclusions." >&2
    return 1
  fi
  prepare_snapshot "${temporary}/source" "${temporary}/files.txt"
  run_cloc "${temporary}/source" "${temporary}/files.txt" "${temporary}/cloc.md"
  parse_cloc_rows "${temporary}/cloc.md" > "${temporary}/rows.tsv"
  write_report "${temporary}/cloc.md" "${temporary}/report.md"
  write_badges "${temporary}/rows.tsv" "${temporary}/badges.md"
}

mode="${1:-}"
temporary="$(mktemp -d)"
trap 'rm -rf "${temporary}"' EXIT

if [[ -n "${REPOSITORY_METRICS_CLOC_BIN:-}" &&
      "${REPOSITORY_METRICS_TEST_MODE:-}" != '1' ]]; then
  echo "REPOSITORY_METRICS_CLOC_BIN is permitted only with REPOSITORY_METRICS_TEST_MODE=1." >&2
  exit 3
fi
if [[ "${REPOSITORY_METRICS_TEST_MODE:-}" == '1' &&
      -z "${REPOSITORY_METRICS_CLOC_BIN:-}" ]]; then
  echo "REPOSITORY_METRICS_TEST_MODE=1 requires an explicit test counter." >&2
  exit 3
fi
if [[ "${mode}" == 'check' && "${REPOSITORY_METRICS_TEST_MODE:-}" != '1' &&
      -f "${OUTPUT}" ]] && grep -Fqx "${TEST_MODE_MARKER}" "${OUTPUT}"; then
  echo "A test-mode repository-metrics report cannot be used as release evidence." >&2
  exit 3
fi

require_clean_source_checkout "${temporary}"

case "${mode}" in
  generate)
    build_candidates "${temporary}"
    cp "${temporary}/report.md" "${OUTPUT}"
    update_readme_badges "${temporary}/badges.md"
    total="$(awk -F '\t' '$1 == "__TOTAL__" { print $2 }' "${temporary}/rows.tsv")"
    languages="$(awk -F '\t' '$1 != "__TOTAL__" { count += 1 } END { print count + 0 }' "${temporary}/rows.tsv")"
    echo "Updated ${OUTPUT#"${ROOT}/"} and README.md (${total} authored LOC; ${languages} languages)."
    ;;
  check)
    build_candidates "${temporary}"
    if ! cmp -s "${OUTPUT}" "${temporary}/report.md"; then
      echo "Committed repository metrics are stale. Regenerate them with:" >&2
      echo "  bash scripts/repository-metrics.sh generate" >&2
      diff -u "${OUTPUT}" "${temporary}/report.md" || true
      exit 1
    fi
    read_readme_badges > "${temporary}/readme-badges.md"
    if ! cmp -s "${temporary}/badges.md" "${temporary}/readme-badges.md"; then
      echo "README repository-metrics badge block is stale. Regenerate it with:" >&2
      echo "  bash scripts/repository-metrics.sh generate" >&2
      diff -u "${temporary}/readme-badges.md" "${temporary}/badges.md" || true
      exit 1
    fi
    total="$(awk -F '\t' '$1 == "__TOTAL__" { print $2 }' "${temporary}/rows.tsv")"
    languages="$(awk -F '\t' '$1 != "__TOTAL__" { count += 1 } END { print count + 0 }' "${temporary}/rows.tsv")"
    echo "Repository metrics and README badges are current (${total} authored LOC; ${languages} languages)."
    ;;
  *)
    usage
    exit 2
    ;;
esac
