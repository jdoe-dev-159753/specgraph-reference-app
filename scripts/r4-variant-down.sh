#!/usr/bin/env bash
# Stops exactly one named R4 Compose project so parallel demo variants cannot tear down each other.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
variant="${1:-baseline}"

if [[ ! "${variant}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
  echo "variant must contain only letters, digits, underscore or hyphen" >&2
  exit 2
fi

project="specgraph-r4-${variant}"
docker compose -p "${project}" -f "${repo_root}/compose.r4.yaml" down --remove-orphans
printf 'R4 variant stopped: project=%s\n' "${project}"
