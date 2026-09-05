#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT/backend/target/source-reference"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-21}"

cd "$ROOT"

# This generated tree is deliberately outside the source authority. Recreate it
# from the maintained Java comments and OpenAPI contract on every invocation.
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

python3 -B -m unittest scripts/test_source_doc_coverage.py
python3 -B scripts/source_doc_coverage.py

if command -v mvn >/dev/null 2>&1; then
  mvn -B -q \
    -f backend/pom.xml \
    "-Dsource.reference.directory=$OUTPUT_DIR" \
    javadoc:javadoc
elif command -v docker >/dev/null 2>&1; then
  docker run --rm \
    -v "$ROOT:/workspace" \
    -w /workspace \
    "$MAVEN_IMAGE" \
    mvn -B -q \
      -f backend/pom.xml \
      -Dsource.reference.directory=/workspace/backend/target/source-reference \
      javadoc:javadoc
else
  echo 'Source-reference generation requires Maven 3.9+ or Docker.' >&2
  exit 1
fi

bash scripts/render-openapi-reference.sh backend/target/source-reference/http-api

for required in "$OUTPUT_DIR/java/index.html" "$OUTPUT_DIR/http-api/index.html"; do
  if [[ ! -s "$required" ]]; then
    echo "Missing generated documentation entry point: $required" >&2
    exit 1
  fi
done

cat > "$OUTPUT_DIR/index.html" <<'HTML'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>SpecGraph source reference</title>
</head>
<body>
  <main>
    <h1>SpecGraph source reference</h1>
    <p>This generated view is derived from maintained source documentation and the repository-owned OpenAPI contract.</p>
    <ul>
      <li><a href="java/index.html">Java implementation reference</a></li>
      <li><a href="http-api/index.html">HTTP API reference</a></li>
    </ul>
  </main>
</body>
</html>
HTML

echo "Generated source reference: $OUTPUT_DIR"
