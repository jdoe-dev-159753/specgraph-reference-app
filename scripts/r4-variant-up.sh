#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
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
if [[ "${backend}" == "openai" && -z "${OPENAI_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY is required only when backend=openai" >&2
  exit 2
fi
if [[ "${backend}" == "local" && -z "${SPECGRAPH_LOCAL_BASE_URL:-}" ]]; then
  echo "SPECGRAPH_LOCAL_BASE_URL is required when backend=local" >&2
  exit 2
fi

project="specgraph-r4-${variant}"
session_cookie_name="${project}_session"

# Keep the browser-visible identifier in a deliberately small cookie-token
# subset and bound its length before Compose receives it.
if [[ ! "${session_cookie_name}" =~ ^[a-zA-Z][a-zA-Z0-9_-]{0,63}$ ]]; then
  echo "derived session cookie name '${session_cookie_name}' is not a safe 1..64 character cookie name" >&2
  exit 2
fi

# A credential supplied for an external gallery variant must not leak into the
# deterministic baseline container through the parent shell environment.
compose_openai_api_key=""
if [[ "${backend}" == "openai" ]]; then
  compose_openai_api_key="${OPENAI_API_KEY}"
fi

compose_local_base_url=""
compose_local_model=""
compose_local_api_key=""
if [[ "${backend}" == "local" ]]; then
  compose_local_base_url="${SPECGRAPH_LOCAL_BASE_URL}"
  compose_local_model="${SPECGRAPH_LOCAL_MODEL:-ministral-3-8b-instruct-2512}"
  compose_local_api_key="${SPECGRAPH_LOCAL_API_KEY:-}"
fi

R4_PORT="${port}" \
R4_SESSION_COOKIE_NAME="${session_cookie_name}" \
SPECGRAPH_ANALYSIS_BACKEND="${backend}" \
OPENAI_API_KEY="" \
SPECGRAPH_OPENAI_API_KEY="${compose_openai_api_key}" \
SPECGRAPH_LOCAL_BASE_URL="${compose_local_base_url}" \
SPECGRAPH_LOCAL_MODEL="${compose_local_model}" \
SPECGRAPH_LOCAL_API_KEY="" \
SPECGRAPH_PROJECTED_LOCAL_API_KEY="${compose_local_api_key}" \
SPECGRAPH_LOCAL_TIMEOUT="${SPECGRAPH_LOCAL_TIMEOUT:-60s}" \
docker compose -p "${project}" -f "${repo_root}/compose.r4.yaml" up --build -d --wait

bash "${script_dir}/r4-variant-manifest.sh" "${variant}" "${port}" "${backend}"
