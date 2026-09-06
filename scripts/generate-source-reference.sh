#!/usr/bin/env bash
# Builds one reviewer artifact while preserving source files as the only maintained authority.
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
python3 -B -m unittest scripts/test_maintained_source_docs.py
python3 -B scripts/maintained_source_docs.py \
  --html backend/target/source-reference/maintained-source/index.html

if command -v mvn >/dev/null 2>&1; then
  mvn -B -q \
    -f backend/pom.xml \
    -Psource-reference \
    "-Dsource.reference.directory=$OUTPUT_DIR" \
    javadoc:javadoc@source-main-reference \
    javadoc:test-javadoc@source-test-reference
elif command -v docker >/dev/null 2>&1; then
  docker run --rm \
    -v "$ROOT:/workspace" \
    -w /workspace \
    "$MAVEN_IMAGE" \
    mvn -B -q \
      -f backend/pom.xml \
      -Psource-reference \
      -Dsource.reference.directory=/workspace/backend/target/source-reference \
      javadoc:javadoc@source-main-reference \
      javadoc:test-javadoc@source-test-reference
else
  echo 'Source-reference generation requires Maven 3.9+ or Docker.' >&2
  exit 1
fi

# Maven Javadoc report goals append their own stable report paths (`apidocs`
# and `testapidocs`) beneath outputDirectory. Promote those complete reports to
# the reviewer-facing names only after proving that each entry point exists.
promote_javadoc_report() {
  local generated="$1"
  local published="$2"
  if [[ ! -s "$generated/index.html" ]]; then
    echo "Missing Maven Javadoc report entry point: $generated/index.html" >&2
    exit 1
  fi
  if [[ -e "$published" ]]; then
    echo "Refusing to replace an existing Javadoc publication path: $published" >&2
    exit 1
  fi
  mv "$generated" "$published"
}

promote_javadoc_report "$OUTPUT_DIR/java/apidocs" "$OUTPUT_DIR/java/main"
promote_javadoc_report "$OUTPUT_DIR/java/testapidocs" "$OUTPUT_DIR/java/tests"

bash scripts/render-frontend-reference.sh backend/target/source-reference/frontend
bash scripts/render-openapi-reference.sh backend/target/source-reference/http-api

for required in \
  "$OUTPUT_DIR/java/main/index.html" \
  "$OUTPUT_DIR/java/tests/index.html" \
  "$OUTPUT_DIR/frontend/index.html" \
  "$OUTPUT_DIR/maintained-source/index.html" \
  "$OUTPUT_DIR/http-api/index.html"; do
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
      <li><a href="java/main/index.html">Java production implementation reference</a></li>
      <li><a href="java/tests/index.html">Java verification-intent reference</a></li>
      <li><a href="frontend/index.html">Browser and end-to-end implementation reference</a></li>
      <li><a href="maintained-source/index.html">Scripts, migrations and executable-configuration reference</a></li>
      <li><a href="http-api/index.html">HTTP API reference</a></li>
    </ul>
  </main>
</body>
</html>
HTML

echo "Generated source reference: $OUTPUT_DIR"
