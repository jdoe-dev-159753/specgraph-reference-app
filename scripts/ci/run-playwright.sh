#!/usr/bin/env bash
# Runs one browser scenario in the pinned dependency image and preserves evidence outside the container.
set -euo pipefail

spec="${1:?usage: run-playwright.sh SPEC_FILE}"
: "${E2E_IMAGE:?E2E_IMAGE must name the immutable Playwright dependency image}"
: "${BASE_URL:?BASE_URL must point at the application under test}"

args=(
  docker run --rm --network host --ipc=host
  --user "$(id -u):$(id -g)"
  -e HOME=/tmp
  -e BASE_URL
  -e PLAYWRIGHT_SPEC="$spec"
  -e EVIDENCE_NAME="${EVIDENCE_NAME:-e2e}"
  -v "$PWD/e2e:/work"
  -w /work
)

if [ -n "${EXPECT_DETECTOR:-}" ]; then
  args+=( -e EXPECT_DETECTOR )
fi
for variable in EXPECT_DETECTORS EXPECT_MODEL_BACKEND EXPECT_MODEL_IDENTITY \
    EXPECT_PROMPT_IDENTITY EXPECT_EXTERNAL_TRANSMISSION EXPECT_DELIVERY_RING; do
  if [ -n "${!variable:-}" ]; then
    args+=( -e "${variable}" )
  fi
done

"${args[@]}" "$E2E_IMAGE" bash -lc '
  set -euo pipefail
  rm -rf /work/node_modules
  ln -s /opt/specgraph-e2e/node_modules /work/node_modules
  cleanup() { rm -f /work/node_modules; }
  trap cleanup EXIT
  playwright test "$PLAYWRIGHT_SPEC"
'
