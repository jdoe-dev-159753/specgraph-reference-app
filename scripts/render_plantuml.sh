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

missing=0
for source in "${SOURCES[@]}"; do
  rendered="${source%.puml}.svg"
  if [[ ! -s "$rendered" ]]; then
    echo "Missing generated SVG for $source: $rendered" >&2
    missing=1
  fi
done

if (( missing != 0 )); then
  exit 1
fi

echo "PlantUML rendering complete."
