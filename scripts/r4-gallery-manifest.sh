#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"

print_variant() {
  local variant="$1"
  local project="specgraph-r4-${variant}"
  local container_id
  local container_state
  local health_state
  local container_environment
  local backend
  local session_cookie_name
  local published_endpoint
  local port

  container_id="$(docker compose -p "${project}" -f "${repo_root}/compose.r4.yaml" ps -q --all r4)"
  if [[ -z "${container_id}" ]]; then
    printf 'R4 variant unavailable\n'
    printf 'composeProject=%s\n' "${project}"
    printf 'runtimeState=stopped\n'
    return
  fi

  container_state="$(docker inspect --format '{{.State.Status}}' "${container_id}")"
  health_state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${container_id}")"
  if [[ "${container_state}" == "running" && "${health_state}" == "healthy" ]]; then
    container_environment="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${container_id}")"
    backend="$(printf '%s\n' "${container_environment}" \
      | sed -n 's/^SPECGRAPH_ANALYSIS_BACKEND=//p' | tail -n 1)"
    backend="${backend:-deterministic}"
    session_cookie_name="$(printf '%s\n' "${container_environment}" \
      | sed -n 's/^SERVER_SERVLET_SESSION_COOKIE_NAME=//p' | tail -n 1)"
    session_cookie_name="${session_cookie_name:-JSESSIONID}"
    published_endpoint="$(docker port "${container_id}" 8080/tcp | head -n 1)"
    if [[ -z "${published_endpoint}" ]]; then
      printf 'R4 variant unavailable\n'
      printf 'composeProject=%s\n' "${project}"
      printf 'runtimeState=running\n'
      printf 'healthState=healthy\n'
      printf 'endpointState=unpublished\n'
      return
    fi
    port="${published_endpoint##*:}"
    bash "${script_dir}/r4-variant-manifest.sh" \
      "${variant}" "${port}" "${backend}" "${session_cookie_name}"
    printf 'runtimeState=healthy\n'
    return
  fi

  printf 'R4 variant unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=%s\n' "${container_state}"
  printf 'healthState=%s\n' "${health_state}"
}

print_variant baseline
printf '\n'
print_variant external
