#!/usr/bin/env bash
# Reports R5 container health and local-model reachability without mutating the running demonstration.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
port="${R5_PORT:-8088}"
model="${SPECGRAPH_LOCAL_MODEL:-ministral-3-8b-instruct-2512}"
preflight_timeout="${R5_PREFLIGHT_TIMEOUT_SECONDS:-10}"
project="specgraph-r5"
compose=(docker compose -p "${project}" -f "${repo_root}/compose.r5.yaml")

if ! command -v docker >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  printf 'R5 runtime unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=missing-runtime-prerequisite\n'
  exit 2
fi
if [[ ! "${preflight_timeout}" =~ ^[0-9]+$ ]] \
    || (( preflight_timeout < 1 || preflight_timeout > 60 )); then
  echo "R5_PREFLIGHT_TIMEOUT_SECONDS must be an integer in 1..60" >&2
  exit 2
fi

container_id="$("${compose[@]}" ps -q --all r5)"
if [[ -z "${container_id}" ]]; then
  printf 'R5 runtime unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=stopped\n'
  exit 1
fi

container_state="$(docker inspect --format '{{.State.Status}}' "${container_id}")"
health_state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${container_id}")"
if [[ "${container_state}" != "running" || "${health_state}" != "healthy" ]]; then
  printf 'R5 runtime unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=%s\n' "${container_state}"
  printf 'healthState=%s\n' "${health_state}"
  exit 1
fi

container_env="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${container_id}")"
env_value() {
  local name="$1"
  printf '%s\n' "${container_env}" | sed -n "s/^${name}=//p" | tail -n 1
}

backend="$(env_value SPECGRAPH_ANALYSIS_BACKEND)"
detector_0="$(env_value SPECGRAPH_ANALYSIS_DETECTORS_0)"
detector_1="$(env_value SPECGRAPH_ANALYSIS_DETECTORS_1)"
detector_2="$(env_value SPECGRAPH_ANALYSIS_DETECTORS_2)"
runtime_model="$(env_value SPECGRAPH_LOCAL_MODEL)"
base_url="$(env_value SPECGRAPH_LOCAL_BASE_URL)"
local_api_key="$(env_value SPECGRAPH_LOCAL_API_KEY)"
session_cookie="$(env_value SERVER_SERVLET_SESSION_COOKIE_NAME)"
context_window_tokens="$(env_value SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS)"
max_output_tokens="$(env_value SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS)"
transport_margin_tokens="$(env_value SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS)"
if [[ "${backend}" != "local" \
      || "${detector_0}" != "BAYESIAN" \
      || "${detector_1}" != "FUZZY" \
      || "${detector_2}" != "RANDOM_FOREST" \
      || "${session_cookie}" != "specgraph-r5_session" \
      || -z "${base_url}" \
      || ! "${runtime_model}" =~ ^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$ \
      || ! "${context_window_tokens}" =~ ^[0-9]+$ \
      || "${context_window_tokens}" -lt 1 \
      || ! "${max_output_tokens}" =~ ^[0-9]+$ \
      || "${max_output_tokens}" -lt 1 \
      || ! "${transport_margin_tokens}" =~ ^[0-9]+$ ]]; then
  printf 'R5 runtime unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=running\n'
  printf 'healthState=healthy\n'
  printf 'configurationState=unexpected\n'
  exit 1
fi

published_endpoint="$(docker port "${container_id}" 8080/tcp | head -n 1)"
if [[ -z "${published_endpoint}" ]]; then
  printf 'R5 runtime unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=running\n'
  printf 'healthState=healthy\n'
  printf 'endpointState=unpublished\n'
  exit 1
fi

umask 077
temp_dir="$(mktemp -d)"
trap 'rm -rf -- "${temp_dir}"' EXIT
lm_header_file="${temp_dir}/lm-studio-header.txt"
lm_curl=(curl --silent --show-error --fail --max-time "${preflight_timeout}")
if [[ -n "${local_api_key}" ]]; then
  if [[ "${local_api_key}" == *$'\n'* || "${local_api_key}" == *$'\r'* ]]; then
    printf 'R5 runtime unavailable\n'
    printf 'composeProject=%s\n' "${project}"
    printf 'lmStudioState=invalid-credential\n'
    exit 1
  fi
  printf 'Authorization: Bearer %s\n' "${local_api_key}" >"${lm_header_file}"
  lm_curl+=(--header "@${lm_header_file}")
fi
models_url="${base_url%/}/models"
if ! models_json="$("${lm_curl[@]}" "${models_url}")"; then
  printf 'R5 runtime unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=running\n'
  printf 'healthState=healthy\n'
  printf 'lmStudioState=unreachable\n'
  exit 1
fi
models_json_compact="$(printf '%s' "${models_json}" | tr -d '[:space:]')"
if [[ "${models_json_compact}" != *"\"id\":\"${runtime_model}\""* ]]; then
  printf 'R5 runtime unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=running\n'
  printf 'healthState=healthy\n'
  printf 'lmStudioState=configured-model-unavailable\n'
  exit 1
fi

port="${published_endpoint##*:}"
publish_address="${published_endpoint%:*}"
model="${runtime_model:-${model}}"
bash "${script_dir}/r5-runtime-manifest.sh" \
  "${port}" "${model}" "not-evaluated-by-status" "not-evaluated-by-status" \
  "${context_window_tokens}" "${max_output_tokens}" "${transport_margin_tokens}" \
  "not-evaluated-by-status" "not-evaluated-by-status" "not-evaluated-by-status" \
  "not-evaluated-by-status" "not-evaluated-by-status" "not-evaluated-by-status" \
  "${publish_address}"
printf 'lmStudioState=ready\n'
printf 'runtimeState=healthy\n'
