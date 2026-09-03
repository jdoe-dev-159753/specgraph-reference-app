#!/usr/bin/env bash
set -euo pipefail

variant="${1:-baseline}"
port="${2:-8084}"
backend="${3:-deterministic}"
project="specgraph-r4-${variant}"

external_transmission=false
if [[ "${backend}" == "openai" ]]; then
  external_transmission=true
fi

cat <<EOF
port=${port}
ring=R4
composeProject=${project}
stage3Backend=${backend}
externalTransmission=${external_transmission}
persistence=isolated-compose-project
EOF
