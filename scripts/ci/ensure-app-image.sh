#!/usr/bin/env bash
# Builds or reuses an application image only when its recipe and requested source revision agree.
set -euo pipefail

requested_tag="${1:?usage: ensure-app-image.sh IMAGE_TAG SOURCE_ROOT SOURCE_REVISION}"
source_root="${2:?usage: ensure-app-image.sh IMAGE_TAG SOURCE_ROOT SOURCE_REVISION}"
source_revision="${3:?usage: ensure-app-image.sh IMAGE_TAG SOURCE_ROOT SOURCE_REVISION}"

command -v docker >/dev/null
command -v flock >/dev/null
command -v sha256sum >/dev/null

recipe_sha="$({
  printf '%s\0' 'docker/app.Dockerfile'
  cat docker/app.Dockerfile
  printf '%s\0' '.dockerignore'
  cat .dockerignore
} | sha256sum | awk '{print $1}')"
image_tag="${requested_tag}-recipe-${recipe_sha:0:12}"

image_matches() {
  docker image inspect "$image_tag" >/dev/null 2>&1 || return 1

  local actual_revision actual_root actual_recipe
  actual_revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image_tag" 2>/dev/null || true)"
  actual_root="$(docker image inspect --format '{{ index .Config.Labels "io.specgraph.source-root" }}' "$image_tag" 2>/dev/null || true)"
  actual_recipe="$(docker image inspect --format '{{ index .Config.Labels "io.specgraph.build-recipe-sha256" }}' "$image_tag" 2>/dev/null || true)"
  [ "$actual_revision" = "$source_revision" ] \
    && [ "$actual_root" = "$source_root" ] \
    && [ "$actual_recipe" = "$recipe_sha" ]
}

if image_matches; then
  echo "Reusing immutable application image ${image_tag}" >&2
  printf '%s\n' "$image_tag"
  exit 0
fi

lock_key="$(printf '%s' "$image_tag" | sha256sum | awk '{print $1}')"
lock_file="/tmp/specgraph-image-${lock_key}.lock"
exec 9>"$lock_file"
flock 9

# Another runner service sharing this Docker daemon may have completed the
# exact immutable build while we waited for the host lock.
if image_matches; then
  echo "Reusing immutable application image ${image_tag} after peer build" >&2
  printf '%s\n' "$image_tag"
  exit 0
fi

if docker image inspect "$image_tag" >/dev/null 2>&1; then
  echo "Replacing stale local tag whose provenance does not match source/build identity: ${image_tag}" >&2
  docker image rm -f "$image_tag" >/dev/null
fi

echo "Building immutable application image ${image_tag} from ${source_root} @ ${source_revision}, recipe ${recipe_sha}" >&2
DOCKER_BUILDKIT=1 docker build \
  -f docker/app.Dockerfile \
  --build-arg "SOURCE_ROOT=${source_root}" \
  --build-arg "SOURCE_REVISION=${source_revision}" \
  --build-arg "BUILD_RECIPE_SHA256=${recipe_sha}" \
  -t "$image_tag" \
  . >&2

if ! image_matches; then
  echo "Built image provenance does not match requested source/build identity: ${image_tag}" >&2
  exit 1
fi

printf '%s\n' "$image_tag"
