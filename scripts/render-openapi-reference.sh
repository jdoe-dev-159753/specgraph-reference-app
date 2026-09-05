#!/usr/bin/env bash
# Renders the canonical OpenAPI contract without committing generated HTML artifacts.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="backend/src/main/resources/static/openapi.yaml"
OUTPUT_DIR="${1:-backend/target/source-reference/http-api}"
OUTPUT="$OUTPUT_DIR/index.html"
REDOCLY_IMAGE="${REDOCLY_IMAGE:-redocly/cli:2.51.2@sha256:2dcc3939c2180e1da96db06a40aa079cb32c4ef3bac8b35ff061f2140322da64}"

cd "$ROOT"

if [[ ! -s "$CONTRACT" ]]; then
  echo "Authoritative OpenAPI contract is missing or empty: $CONTRACT" >&2
  exit 1
fi

if [[ "$OUTPUT_DIR" = /* || "$OUTPUT_DIR" =~ ^[A-Za-z]:[/\\] || "/$OUTPUT_DIR/" == *"/../"* ]]; then
  echo "Output directory must be inside the repository: $OUTPUT_DIR" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

docker run --rm \
  --network none \
  --user "$(id -u):$(id -g)" \
  -e LC_ALL=C.UTF-8 \
  -e SOURCE_DATE_EPOCH=0 \
  -e TZ=UTC \
  -v "$ROOT:/spec" \
  -w /spec \
  "$REDOCLY_IMAGE" \
  build-docs "$CONTRACT" \
  --disableGoogleFont \
  --title "Customer Activity Analytics HTTP API" \
  --output "$OUTPUT"

if [[ ! -s "$OUTPUT" ]] || ! grep -qi '<html' "$OUTPUT"; then
  echo "Redocly did not produce a browsable HTML document: $OUTPUT" >&2
  exit 1
fi

echo "HTTP API reference generated from $CONTRACT: $OUTPUT"
