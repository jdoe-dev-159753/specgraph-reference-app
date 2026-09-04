#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
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

# A credential supplied for an external gallery variant must not leak into the
# deterministic baseline container through the parent shell environment.
compose_openai_api_key=""
if [[ "${backend}" == "openai" ]]; then
  compose_openai_api_key="${OPENAI_API_KEY}"
fi

R4_PORT="${port}" \
SPECGRAPH_ANALYSIS_BACKEND="${backend}" \
OPENAI_API_KEY="" \
SPECGRAPH_OPENAI_API_KEY="${compose_openai_api_key}" \
docker compose -p "${project}" -f "${repo_root}/compose.r4.yaml" up --build -d --wait

bash "${script_dir}/r4-variant-manifest.sh" "${variant}" "${port}" "${backend}"
