#!/usr/bin/env bash
set -euo pipefail

variant="${1:-baseline}"
port="${2:-8084}"
backend="${3:-deterministic}"

case "${backend}" in
  deterministic|openai) ;;
  local)
    echo "backend 'local' is reserved by the typed contract but is not implemented yet (#251)" >&2
    exit 2
    ;;
  *)
    echo "unsupported backend '${backend}'; expected deterministic or openai" >&2
    exit 2
    ;;
esac

if [[ ! "${variant}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
  echo "variant must contain only letters, digits, underscore or hyphen" >&2
  exit 2
fi
if [[ ! "${port}" =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
  echo "port must be an integer in 1..65535" >&2
  exit 2
fi
if [[ "${backend}" == "openai" && -z "${OPENAI_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY is required only when backend=openai" >&2
  exit 2
fi

project="specgraph-r4-${variant}"
external_transmission=false
if [[ "${backend}" == "openai" ]]; then
  external_transmission=true
fi

R4_PORT="${port}" \
SPECGRAPH_ANALYSIS_BACKEND="${backend}" \
docker compose -p "${project}" -f compose.r4.yaml up --build -d --wait

printf '%s\n' \
  "R4 variant ready" \
  "  project=${project}" \
  "  url=http://localhost:${port}/" \
  "  ring=R4" \
  "  stage3.backend=${backend}" \
  "  externalTransmission=${external_transmission}" \
  "  persistence=isolated-compose-project"
