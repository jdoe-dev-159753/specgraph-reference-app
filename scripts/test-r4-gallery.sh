#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

mkdir -p "${temp_dir}/bin"
docker_log="${temp_dir}/docker.log"
cat > "${temp_dir}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
ambient_credential_state=absent
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  ambient_credential_state=present
fi
projected_credential_state=absent
if [[ -n "${SPECGRAPH_OPENAI_API_KEY:-}" ]]; then
  projected_credential_state=present
fi
local_endpoint_state=absent
[[ -n "${SPECGRAPH_LOCAL_BASE_URL:-}" ]] && local_endpoint_state=present
local_credential_state=absent
[[ -n "${SPECGRAPH_LOCAL_API_KEY:-}" ]] && local_credential_state=present
projected_local_credential_state=absent
[[ -n "${SPECGRAPH_PROJECTED_LOCAL_API_KEY:-}" ]] && projected_local_credential_state=present
printf 'ambient-credential=%s projected-credential=%s local-endpoint=%s local-credential=%s projected-local-credential=%s %s\n' \
  "${ambient_credential_state}" "${projected_credential_state}" \
  "${local_endpoint_state}" "${local_credential_state}" \
  "${projected_local_credential_state}" "$*" >> "${R4_TEST_DOCKER_LOG}"
if [[ "$*" == *"compose -p specgraph-r4-baseline"* && "$*" == *" ps -q --all r4"* ]]; then
  printf '%s\n' "${R4_TEST_BASELINE_CONTAINER:-}"
  exit 0
fi
if [[ "$*" == *"compose -p specgraph-r4-external"* && "$*" == *" ps -q --all r4"* ]]; then
  printf '%s\n' "${R4_TEST_EXTERNAL_CONTAINER:-}"
  exit 0
fi
if [[ "$*" == *"inspect --format {{.State.Status}} baseline-container"* ]]; then
  printf '%s\n' "${R4_TEST_BASELINE_STATE:-running}"
  exit 0
fi
if [[ "$*" == *"inspect --format {{.State.Status}} external-container"* ]]; then
  printf '%s\n' "${R4_TEST_EXTERNAL_STATE:-running}"
  exit 0
fi
if [[ "$*" == *"inspect --format {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} baseline-container"* ]]; then
  printf '%s\n' "${R4_TEST_BASELINE_HEALTH:-healthy}"
  exit 0
fi
if [[ "$*" == *"inspect --format {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} external-container"* ]]; then
  printf '%s\n' "${R4_TEST_EXTERNAL_HEALTH:-healthy}"
  exit 0
fi
if [[ "$*" == *"inspect --format {{range .Config.Env}}{{println .}}{{end}} baseline-container"* ]]; then
  printf 'SPECGRAPH_ANALYSIS_BACKEND=%s\n' "${R4_TEST_BASELINE_BACKEND:-deterministic}"
  exit 0
fi
if [[ "$*" == *"inspect --format {{range .Config.Env}}{{println .}}{{end}} external-container"* ]]; then
  printf 'SPECGRAPH_ANALYSIS_BACKEND=%s\n' "${R4_TEST_EXTERNAL_BACKEND:-openai}"
  exit 0
fi
if [[ "$*" == *"port baseline-container 8080/tcp"* ]]; then
  printf '0.0.0.0:%s\n' "${R4_TEST_BASELINE_PORT:-8084}"
  exit 0
fi
if [[ "$*" == *"port external-container 8080/tcp"* ]]; then
  printf '0.0.0.0:%s\n' "${R4_TEST_EXTERNAL_PORT:-8087}"
  exit 0
fi
if [[ "${R4_TEST_FAIL_BASELINE:-false}" == "true" && "$*" == *"specgraph-r4-baseline"* && "$*" == *" up "* ]]; then
  exit 99
fi
if [[ "${R4_TEST_FAIL_BASELINE_DOWN:-false}" == "true" && "$*" == *"specgraph-r4-baseline"* && "$*" == *" down "* ]]; then
  exit 98
fi
if [[ "${R4_TEST_FAIL_EXTERNAL_DOWN:-false}" == "true" && "$*" == *"specgraph-r4-external"* && "$*" == *" down "* ]]; then
  exit 97
fi
EOF
chmod +x "${temp_dir}/bin/docker"

PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
env -u OPENAI_API_KEY bash "${script_dir}/r4-gallery-up.sh" \
  > "${temp_dir}/stdout" 2> "${temp_dir}/stderr"

grep -Fq "compose -p specgraph-r4-external" "${docker_log}"
grep -Fq "down --remove-orphans" "${docker_log}"
if grep -F "compose -p specgraph-r4-external" "${docker_log}" | grep -Fq " up "; then
  echo "credential-free gallery unexpectedly started the external variant" >&2
  exit 1
fi
grep -Fq "the optional external R4 variant is stopped" "${temp_dir}/stderr"
if grep -Fq "url=http://localhost:8087/" "${temp_dir}/stdout"; then
  echo "credential-free gallery unexpectedly advertised the external variant" >&2
  exit 1
fi

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R4_TEST_DOCKER_LOG="${docker_log}" \
  R4_TEST_FAIL_BASELINE=true \
  env -u OPENAI_API_KEY bash "${script_dir}/r4-gallery-up.sh" \
    > "${temp_dir}/failed-stdout" 2> "${temp_dir}/failed-stderr"; then
  echo "injected baseline failure unexpectedly succeeded" >&2
  exit 1
fi
first_command="$(head -n 1 "${docker_log}")"
if [[ "${first_command}" != *"specgraph-r4-external"* || "${first_command}" != *"down --remove-orphans"* ]]; then
  echo "external opt-out did not precede the failing baseline startup" >&2
  exit 1
fi

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
OPENAI_API_KEY=test-only-key \
bash "${script_dir}/r4-gallery-up.sh" \
  > "${temp_dir}/keyed-stdout" 2> "${temp_dir}/keyed-stderr"
baseline_up="$(grep -F "specgraph-r4-baseline" "${docker_log}" | grep -F " up ")"
external_up="$(grep -F "specgraph-r4-external" "${docker_log}" | grep -F " up ")"
if [[ "${baseline_up}" != "ambient-credential=absent projected-credential=absent"* ]]; then
  echo "deterministic baseline inherited the OpenAI credential" >&2
  exit 1
fi
if [[ "${external_up}" != "ambient-credential=absent projected-credential=present"* ]]; then
  echo "OpenAI variant did not receive its deliberate credential" >&2
  exit 1
fi

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
SPECGRAPH_LOCAL_MODEL=ministral-test \
SPECGRAPH_LOCAL_API_KEY=test-only-local-key \
bash "${script_dir}/r4-variant-up.sh" local 8086 local \
  > "${temp_dir}/local-stdout" 2> "${temp_dir}/local-stderr"
local_up="$(grep -F "specgraph-r4-local" "${docker_log}" | grep -F " up ")"
if [[ "${local_up}" != *"projected-credential=absent local-endpoint=present local-credential=absent projected-local-credential=present"* ]]; then
  echo "local variant did not isolate its endpoint/token from the OpenAI credential path" >&2
  exit 1
fi

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
SPECGRAPH_LOCAL_API_KEY=ambient-local-key \
bash "${script_dir}/r4-variant-up.sh" baseline 8084 deterministic \
  > "${temp_dir}/ambient-local-stdout" 2> "${temp_dir}/ambient-local-stderr"
baseline_with_ambient_local="$(grep -F "specgraph-r4-baseline" "${docker_log}" | grep -F " up ")"
if [[ "${baseline_with_ambient_local}" != *"local-credential=absent projected-local-credential=absent"* ]]; then
  echo "deterministic baseline inherited the ambient local credential" >&2
  exit 1
fi
grep -Fq "stage3Backend=local" "${temp_dir}/local-stdout"
grep -Fq "externalTransmission=false" "${temp_dir}/local-stdout"
grep -Fq "stage3Runtime=lmstudio/llama.cpp" "${temp_dir}/local-stdout"

if PATH="${temp_dir}/bin:${PATH}" R4_TEST_DOCKER_LOG="${docker_log}" \
  env -u SPECGRAPH_LOCAL_BASE_URL bash "${script_dir}/r4-variant-up.sh" local 8086 local; then
  echo "local variant without an endpoint unexpectedly succeeded" >&2
  exit 1
fi

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
R4_TEST_BASELINE_CONTAINER=baseline-container \
R4_TEST_EXTERNAL_CONTAINER=external-container \
R4_TEST_EXTERNAL_PORT=9097 \
env -u OPENAI_API_KEY bash "${script_dir}/r4-gallery-manifest.sh" \
  > "${temp_dir}/running-manifest"
grep -Fq "composeProject=specgraph-r4-baseline" "${temp_dir}/running-manifest"
grep -Fq "composeProject=specgraph-r4-external" "${temp_dir}/running-manifest"
grep -Fq "url=http://localhost:9097/" "${temp_dir}/running-manifest"
grep -Fq "stage3Backend=openai" "${temp_dir}/running-manifest"
if [[ "$(grep -Fc 'runtimeState=healthy' "${temp_dir}/running-manifest")" != "2" ]]; then
  echo "standalone manifest did not report both running variants from Compose state" >&2
  exit 1
fi

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
R4_TEST_BASELINE_CONTAINER=baseline-container \
R4_TEST_EXTERNAL_CONTAINER=external-container \
R4_TEST_EXTERNAL_HEALTH=unhealthy \
env -u OPENAI_API_KEY bash "${script_dir}/r4-gallery-manifest.sh" \
  > "${temp_dir}/unhealthy-manifest"
grep -A3 -F "composeProject=specgraph-r4-external" "${temp_dir}/unhealthy-manifest" \
  | grep -Fq "healthState=unhealthy"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R4_TEST_DOCKER_LOG="${docker_log}" \
  R4_TEST_FAIL_BASELINE_DOWN=true \
  bash "${script_dir}/r4-gallery-down.sh"; then
  echo "failing baseline teardown unexpectedly succeeded" >&2
  exit 1
fi
grep -Fq "specgraph-r4-baseline" "${docker_log}"
grep -Fq "specgraph-r4-external" "${docker_log}"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R4_TEST_DOCKER_LOG="${docker_log}" \
  R4_TEST_FAIL_EXTERNAL_DOWN=true \
  bash "${script_dir}/r4-gallery-down.sh"; then
  echo "failing external teardown unexpectedly succeeded" >&2
  exit 1
fi
grep -Fq "specgraph-r4-baseline" "${docker_log}"
grep -Fq "specgraph-r4-external" "${docker_log}"

echo "R4 gallery lifecycle tests passed"
