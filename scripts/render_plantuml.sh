#!/usr/bin/env bash
# Regenerates controlled diagram views from PlantUML/DOT authorities using one pinned renderer image.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# One pinned container owns both renderer implementations: PlantUML for .puml sources
# and the bundled Graphviz `dot` binary for the legacy .dot source. This keeps local
# regeneration and CI on the same reproducible renderer/runtime provenance.
PLANTUML_IMAGE="${PLANTUML_IMAGE:-plantuml/plantuml:1.2026.6@sha256:47870c1f76cfb3747bc7090bfe83013a4e3105b5a0bb1515e2baf5d3e2b3ee9d}"

mapfile -d '' PLANTUML_SOURCES < <(find docs/assignment -type f -name '*.puml' -print0 | sort -z)
mapfile -d '' DOT_SOURCES < <(find docs/assignment -type f -name '*.dot' -print0 | sort -z)

if (( ${#PLANTUML_SOURCES[@]} == 0 && ${#DOT_SOURCES[@]} == 0 )); then
  echo "No controlled diagram sources found under docs/assignment." >&2
  exit 1
fi

if (( ${#PLANTUML_SOURCES[@]} > 0 )); then
  echo "Rendering ${#PLANTUML_SOURCES[@]} PlantUML source(s) with ${PLANTUML_IMAGE}"
  docker run --rm \
    --network none \
    --user "$(id -u):$(id -g)" \
    -v "$ROOT:/workspace" \
    -w /workspace \
    "$PLANTUML_IMAGE" \
    -charset UTF-8 \
    -tsvg \
    "${PLANTUML_SOURCES[@]}"
fi

if (( ${#DOT_SOURCES[@]} > 0 )); then
  echo "Rendering ${#DOT_SOURCES[@]} Graphviz DOT source(s) with Graphviz from ${PLANTUML_IMAGE}"
  for source in "${DOT_SOURCES[@]}"; do
    rendered="${source%.dot}.svg"
    docker run --rm \
      --network none \
      --user "$(id -u):$(id -g)" \
      -v "$ROOT:/workspace" \
      -w /workspace \
      --entrypoint dot \
      "$PLANTUML_IMAGE" \
      -Tsvg "$source" -o "$rendered"
  done
fi

invalid=0
validate_svg() {
  local source="$1"
  local rendered="$2"

  if [[ ! -s "$rendered" ]]; then
    echo "Missing generated SVG for $source: $rendered" >&2
    invalid=1
    return
  fi

  # Some renderer paths can emit diagnostics before the SVG document. Diagnostics are
  # not part of the committed artifact. Strip only a leading preamble, then require
  # well-formed SVG XML for every controlled generated view.
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
}

for source in "${PLANTUML_SOURCES[@]}"; do
  validate_svg "$source" "${source%.puml}.svg"
done

for source in "${DOT_SOURCES[@]}"; do
  validate_svg "$source" "${source%.dot}.svg"
done

# Diagram views in controlled documentation must retain an explicit sibling source.
# PlantUML views use .puml and Graphviz views use .dot. This invariant deliberately
# does not depend on renderer metadata, so @startdot output is covered even when it
# carries no <?plantuml processing-instruction marker.
while IFS= read -r -d '' rendered; do
  puml_source="${rendered%.svg}.puml"
  dot_source="${rendered%.svg}.dot"
  if [[ ! -f "$puml_source" && ! -f "$dot_source" ]]; then
    echo "Orphaned controlled SVG without authoritative sibling source: $rendered" >&2
    invalid=1
  fi
done < <(git ls-files -z 'docs/assignment/**/diagrams/*.svg')

if (( invalid != 0 )); then
  exit 1
fi

echo "Controlled diagram rendering complete."
