#!/usr/bin/env bash
# Prints the configured and observed identities that let a reviewer distinguish each R4 backend.
set -euo pipefail

variant="${1:-baseline}"
port="${2:-8084}"
backend="${3:-deterministic}"

case "${backend}" in
  deterministic|openai|local) ;;
  *)
    echo "unsupported backend '${backend}'; expected deterministic, openai or local" >&2
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
session_cookie_name="${4:-${project}_session}"

if [[ ! "${session_cookie_name}" =~ ^[a-zA-Z][a-zA-Z0-9_-]{0,63}$ ]]; then
  echo "session cookie name '${session_cookie_name}' is not a safe 1..64 character cookie name" >&2
  exit 2
fi

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
sessionCookieName=${session_cookie_name}
stage3Backend=${backend}
externalTransmission=${external_transmission}
persistence=isolated-compose-project
EOF

if [[ "${backend}" == "local" ]]; then
  printf 'stage3Runtime=lmstudio/llama.cpp\n'
fi
