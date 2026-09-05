#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"

docker compose -p specgraph-r5 -f "${repo_root}/compose.r5.yaml" down -v --remove-orphans
printf 'R5 runtime stopped: project=specgraph-r5\n'
