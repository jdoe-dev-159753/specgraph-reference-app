#!/usr/bin/env bash
set -euo pipefail

command -v docker >/dev/null
command -v flock >/dev/null
command -v sha256sum >/dev/null

inputs_sha="$({
  printf '%s\0' 'e2e/package.json'
  cat e2e/package.json
  printf '%s\0' 'docker/e2e.Dockerfile'
  cat docker/e2e.Dockerfile
} | sha256sum | awk '{print $1}')"
image_tag="specgraph-reference-app:e2e-deps-${inputs_sha:0:24}"
lock_file="/tmp/specgraph-e2e-${inputs_sha}.lock"

image_matches() {
  docker image inspect "$image_tag" >/dev/null 2>&1 || return 1
  local actual
  actual="$(docker image inspect --format '{{ index .Config.Labels "io.specgraph.e2e.inputs-sha256" }}' "$image_tag" 2>/dev/null || true)"
  [ "$actual" = "$inputs_sha" ]
}

if image_matches; then
  echo "Reusing immutable Playwright dependency image ${image_tag}" >&2
  printf '%s\n' "$image_tag"
  exit 0
fi

exec 9>"$lock_file"
flock 9

if image_matches; then
  echo "Reusing immutable Playwright dependency image ${image_tag} after peer build" >&2
  printf '%s\n' "$image_tag"
  exit 0
fi

if docker image inspect "$image_tag" >/dev/null 2>&1; then
  docker image rm -f "$image_tag" >/dev/null
fi

echo "Building immutable Playwright dependency image ${image_tag}" >&2
DOCKER_BUILDKIT=1 docker build \
  -f docker/e2e.Dockerfile \
  --build-arg "E2E_INPUTS_SHA256=${inputs_sha}" \
  -t "$image_tag" \
  . >&2

if ! image_matches; then
  echo "Built E2E image does not retain the requested committed-input provenance" >&2
  exit 1
fi

printf '%s\n' "$image_tag"
