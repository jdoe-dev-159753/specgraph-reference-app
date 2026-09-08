#!/usr/bin/env bash
# Builds one reviewer artifact while preserving source files as the only maintained authority.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT/backend/target/source-reference"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8"

cd "$ROOT"

# The immutable OCI inventory is maintained source authority too. Execute the
# offline regression suite from this PR-head script so pull_request_target
# proves the candidate bytes even before the workflow definition itself merges.
python3 -B -m unittest scripts/test_verify_container_images.py
python3 -B scripts/verify_container_images.py

# This generated tree is deliberately outside the source authority. Recreate it
# from the maintained Java comments and OpenAPI contract on every invocation.
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Recompute repository metrics from the exact checked-out head without allowing
# generated badge/report bytes to become maintained source implicitly. The
# candidate is retained in the reviewer artifact; tracked files are restored
# byte-for-byte from HEAD before the rest of source-reference generation runs.
python3 -B -m unittest scripts/test_repository_metrics.py
restore_metric_sources() {
  git show HEAD:README.md > README.md
  git show HEAD:docs/reviewer/repository-metrics.md > docs/reviewer/repository-metrics.md
}
trap restore_metric_sources EXIT
bash scripts/repository-metrics.sh generate
mkdir -p "$OUTPUT_DIR/repository-metrics-candidate"
cp README.md "$OUTPUT_DIR/repository-metrics-candidate/README.md"
cp docs/reviewer/repository-metrics.md \
  "$OUTPUT_DIR/repository-metrics-candidate/repository-metrics.md"
git rev-parse HEAD > "$OUTPUT_DIR/repository-metrics-candidate/source-sha.txt"
restore_metric_sources
trap - EXIT

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
    --user "$(id -u):$(id -g)" \
    --env HOME=/tmp/specgraph-maven-home \
    --env MAVEN_CONFIG=/tmp/specgraph-maven-home/.m2 \
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
      <li><a href="repository-metrics-candidate/repository-metrics.md">Exact-head repository metrics candidate</a></li>
    </ul>
  </main>
</body>
</html>
HTML

echo "Generated source reference: $OUTPUT_DIR"
