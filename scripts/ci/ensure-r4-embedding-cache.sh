#!/usr/bin/env bash
set -euo pipefail

command -v docker >/dev/null
command -v flock >/dev/null
command -v sha256sum >/dev/null

config=backend/src/main/resources/application-r4.yml
config_sha="$(sha256sum "$config" | awk '{print $1}')"
volume="specgraph-r4-embedding-${config_sha:0:20}"
lock_file="/tmp/${volume}.lock"

exec 9>"$lock_file"
flock 9

if ! docker volume inspect "$volume" >/dev/null 2>&1; then
  docker volume create \
    --label io.specgraph.cache=r4-embedding \
    --label "io.specgraph.r4-config-sha256=${config_sha}" \
    "$volume" >/dev/null
fi

actual_sha="$(docker volume inspect --format '{{ index .Labels "io.specgraph.r4-config-sha256" }}' "$volume" 2>/dev/null || true)"
if [ "$actual_sha" != "$config_sha" ]; then
  echo "Embedding cache volume provenance mismatch: ${volume}" >&2
  exit 1
fi

# The application image runs as uid/gid 10001. Initialize the persistent cache
# root once so disposable R4 containers can write transformer resources safely.
docker run --rm -v "${volume}:/cache" alpine:3.22 \
  sh -c 'chown -R 10001:10001 /cache' >/dev/null

printf '%s\n' "$volume"
