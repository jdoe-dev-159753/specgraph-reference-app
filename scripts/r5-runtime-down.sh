#!/usr/bin/env bash
# Stops R5 while retaining the costly embedding cache unless destructive purge is explicitly requested.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"

down_args=(down --remove-orphans)
cache_state="retained"
if [[ "${1:-}" == "--purge" ]]; then
  down_args=(down -v --remove-orphans)
  cache_state="purged"
elif (( $# != 0 )); then
  echo "usage: $0 [--purge]" >&2
  exit 2
fi

docker compose -p specgraph-r5 -f "${repo_root}/compose.r5.yaml" "${down_args[@]}"
printf 'R5 runtime stopped: project=specgraph-r5 embeddingCache=%s\n' "${cache_state}"
