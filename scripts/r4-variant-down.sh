#!/usr/bin/env bash
set -euo pipefail

variant="${1:-baseline}"

if [[ ! "${variant}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
  echo "variant must contain only letters, digits, underscore or hyphen" >&2
  exit 2
fi

project="specgraph-r4-${variant}"
docker compose -p "${project}" -f compose.r4.yaml down --remove-orphans
printf 'R4 variant stopped: project=%s\n' "${project}"
