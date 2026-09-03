#!/usr/bin/env bash
set -euo pipefail

command -v docker >/dev/null
command -v flock >/dev/null
command -v sha256sum >/dev/null
command -v date >/dev/null

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

# Bound long-lived repository-owned model caches without racing active or freshly
# created configurations. Keep the current cache, keep the two newest eligible
# superseded caches, and give every newly-created cache a 24-hour grace window.
gc_lock=/tmp/specgraph-r4-embedding-gc.lock
exec 8>"$gc_lock"
flock 8
now_epoch="$(date +%s)"
eligible=()
while IFS=' ' read -r created_epoch candidate; do
  [ -n "${candidate:-}" ] || continue
  eligible+=("$candidate")
done < <(
  for candidate in $(docker volume ls -q --filter label=io.specgraph.cache=r4-embedding); do
    [ "$candidate" = "$volume" ] && continue
    if [ -n "$(docker ps -aq --filter "volume=$candidate")" ]; then
      continue
    fi
    created="$(docker volume inspect --format '{{.CreatedAt}}' "$candidate" 2>/dev/null || true)"
    created_epoch="$(date -d "$created" +%s 2>/dev/null || echo 0)"
    [ "$created_epoch" -gt 0 ] || continue
    age_seconds=$((now_epoch - created_epoch))
    [ "$age_seconds" -ge 86400 ] || continue
    printf '%s %s\n' "$created_epoch" "$candidate"
  done | sort -nr
)

for index in "${!eligible[@]}"; do
  [ "$index" -lt 2 ] && continue
  candidate="${eligible[$index]}"
  if docker volume rm "$candidate" >/dev/null 2>&1; then
    echo "Reclaimed superseded R4 embedding cache: $candidate" >&2
  fi
done

printf '%s\n' "$volume"
