#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
port="${R5_PORT:-8088}"
model="${SPECGRAPH_LOCAL_MODEL:-ministral-3-8b-instruct-2512}"
project="specgraph-r5"
compose=(docker compose -p "${project}" -f "${repo_root}/compose.r5.yaml")

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
session_cookie="$(env_value SERVER_SERVLET_SESSION_COOKIE_NAME)"
context_window_tokens="$(env_value SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS)"
max_output_tokens="$(env_value SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS)"
transport_margin_tokens="$(env_value SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS)"
if [[ "${backend}" != "local" \
      || "${detector_0}" != "BAYESIAN" \
      || "${detector_1}" != "FUZZY" \
      || "${detector_2}" != "RANDOM_FOREST" \
      || "${session_cookie}" != "specgraph-r5_session" \
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

port="${published_endpoint##*:}"
model="${runtime_model:-${model}}"
bash "${script_dir}/r5-runtime-manifest.sh" \
  "${port}" "${model}" "${context_window_tokens}" "${max_output_tokens}" \
  "${transport_margin_tokens}" "not-evaluated-by-status"
printf 'runtimeState=healthy\n'
