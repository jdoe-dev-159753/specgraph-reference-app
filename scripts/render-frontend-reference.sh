#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOL_DIR="$ROOT/docs/tooling/frontend-reference"
OUTPUT_DIR="${1:-backend/target/source-reference/frontend}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

cd "$ROOT"

if [[ "$OUTPUT_DIR" = /* || "$OUTPUT_DIR" =~ ^[A-Za-z]:[/\\] || "/$OUTPUT_DIR/" == *"/../"* ]]; then
  echo "Output directory must be inside the repository: $OUTPUT_DIR" >&2
  exit 1
fi

"$PYTHON_BIN" scripts/check-frontend-source-reference.py
npm ci --prefix "$TOOL_DIR" --ignore-scripts --no-audit --no-fund
"$TOOL_DIR/node_modules/.bin/typedoc" \
  --options "$TOOL_DIR/typedoc.json" \
  --out "$OUTPUT_DIR"

if [[ ! -s "$OUTPUT_DIR/index.html" ]]; then
  echo "TypeDoc did not produce a browsable frontend reference: $OUTPUT_DIR/index.html" >&2
  exit 1
fi

echo "Frontend source reference generated: $OUTPUT_DIR/index.html"
