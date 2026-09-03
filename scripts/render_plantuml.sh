#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PLANTUML_IMAGE="${PLANTUML_IMAGE:-plantuml/plantuml:1.2026.6@sha256:47870c1f76cfb3747bc7090bfe83013a4e3105b5a0bb1515e2baf5d3e2b3ee9d}"

mapfile -d '' SOURCES < <(find docs/assignment -type f -name '*.puml' -print0 | sort -z)
if (( ${#SOURCES[@]} == 0 )); then
  echo "No PlantUML sources found under docs/assignment." >&2
  exit 1
fi

echo "Rendering ${#SOURCES[@]} PlantUML source(s) with ${PLANTUML_IMAGE}"

docker run --rm \
  --network none \
  --user "$(id -u):$(id -g)" \
  -v "$ROOT:/workspace" \
  -w /workspace \
  "$PLANTUML_IMAGE" \
  -charset UTF-8 \
  -tsvg \
  "${SOURCES[@]}"

invalid=0
for source in "${SOURCES[@]}"; do
  rendered="${source%.puml}.svg"
  if [[ ! -s "$rendered" ]]; then
    echo "Missing generated SVG for $source: $rendered" >&2
    invalid=1
    continue
  fi

  # Graphviz diagnostics emitted by @startdot can precede the SVG document in some
  # PlantUML container builds. They are renderer diagnostics, not part of the artifact.
  # Strip only a leading diagnostic preamble, then require valid SVG XML.
  if ! python3 - "$rendered" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
starts = [index for token in ("<?xml", "<svg") if (index := text.find(token)) >= 0]
if not starts:
    raise SystemExit(f"{path}: no SVG/XML document start found")
start = min(starts)
if start:
    preamble = text[:start].strip()
    if preamble:
        print(f"{path}: removing renderer diagnostic preamble: {preamble}", file=sys.stderr)
    path.write_text(text[start:], encoding="utf-8")
try:
    root = ET.parse(path).getroot()
except ET.ParseError as exc:
    raise SystemExit(f"{path}: malformed SVG XML: {exc}") from exc
if root.tag.split("}")[-1] != "svg":
    raise SystemExit(f"{path}: XML root is not <svg>: {root.tag}")
PY
  then
    invalid=1
  fi
done

# A retained PlantUML-generated SVG with no sibling source is stale design evidence.
# Hand-authored/separately sourced SVGs are intentionally ignored because they do not
# carry PlantUML's embedded processing-instruction marker.
while IFS= read -r -d '' rendered; do
  if grep -q '<\?plantuml ' "$rendered"; then
    source="${rendered%.svg}.puml"
    if [[ ! -f "$source" ]]; then
      echo "Orphaned generated PlantUML SVG without authoritative source: $rendered" >&2
      invalid=1
    fi
  fi
done < <(git ls-files -z 'docs/assignment/**/*.svg')

if (( invalid != 0 )); then
  exit 1
fi

echo "PlantUML rendering complete."
