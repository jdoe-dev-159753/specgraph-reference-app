#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"

print_variant() {
  local variant="$1"
  local port="$2"
  local backend="$3"
  local project="specgraph-r4-${variant}"
  local container_id
  local container_state
  local health_state

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
    bash "${script_dir}/r4-variant-manifest.sh" "${variant}" "${port}" "${backend}"
    printf 'runtimeState=healthy\n'
    return
  fi

  printf 'R4 variant unavailable\n'
  printf 'composeProject=%s\n' "${project}"
  printf 'runtimeState=%s\n' "${container_state}"
  printf 'healthState=%s\n' "${health_state}"
}

print_variant baseline 8084 deterministic
printf '\n'
print_variant external 8087 openai
