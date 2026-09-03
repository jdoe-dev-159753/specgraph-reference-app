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

project="specgraph-r4-${variant}"

external_transmission=false
if [[ "${backend}" == "openai" ]]; then
  external_transmission=true
fi

cat <<EOF
R4 variant ready
url=http://localhost:${port}/
port=${port}
ring=R4
composeProject=${project}
stage3Backend=${backend}
externalTransmission=${external_transmission}
persistence=isolated-compose-project
EOF
