#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
port="${R5_PORT:-8088}"
bind_address="${R5_BIND_ADDRESS:-127.0.0.1}"
model="${SPECGRAPH_LOCAL_MODEL:-ministral-3-8b-instruct-2512}"
base_url="${SPECGRAPH_LOCAL_BASE_URL:-}"
local_api_key="${SPECGRAPH_LOCAL_API_KEY:-}"
analysis_timeout="${R5_ANALYSIS_TIMEOUT_SECONDS:-90}"
preflight_timeout="${R5_PREFLIGHT_TIMEOUT_SECONDS:-10}"
context_window_tokens="${SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS:-4096}"
max_output_tokens="${SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS:-512}"
transport_margin_tokens="${SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS:-256}"
source_build="${R5_SOURCE_BUILD:-false}"
project="specgraph-r5"
compose=(docker compose -p "${project}" -f "${repo_root}/compose.r5.yaml")
temp_dir=""
stack_requires_cleanup=false

cleanup_failed_start() {
  "${compose[@]}" down --remove-orphans >/dev/null 2>&1 || true
}

cleanup_on_exit() {
  local exit_status=$?
  if [[ -n "${temp_dir}" && -d "${temp_dir}" ]]; then
    rm -rf -- "${temp_dir}"
  fi
  if (( exit_status != 0 )) && [[ "${stack_requires_cleanup}" == "true" ]]; then
    cleanup_failed_start
  fi
}
trap cleanup_on_exit EXIT

if [[ -z "${base_url}" ]]; then
  echo "SPECGRAPH_LOCAL_BASE_URL is required (for example http://192.168.1.20:1234/v1)" >&2
  exit 2
fi
if [[ ! "${port}" =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
  echo "R5_PORT must be an integer in 1..65535" >&2
  exit 2
fi
if [[ -z "${bind_address}" || "${bind_address}" == *[[:space:]/]* ]]; then
  echo "R5_BIND_ADDRESS must be a non-empty host address without whitespace or '/'" >&2
  exit 2
fi
if [[ ! "${model}" =~ ^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$ ]]; then
  echo "SPECGRAPH_LOCAL_MODEL must be a safe single-line model identifier" >&2
  exit 2
fi
if [[ ! "${analysis_timeout}" =~ ^[0-9]+$ ]] \
    || (( analysis_timeout < 1 || analysis_timeout > 300 )); then
  echo "R5_ANALYSIS_TIMEOUT_SECONDS must be an integer in 1..300" >&2
  exit 2
fi
if [[ ! "${preflight_timeout}" =~ ^[0-9]+$ ]] \
    || (( preflight_timeout < 1 || preflight_timeout > 60 )); then
  echo "R5_PREFLIGHT_TIMEOUT_SECONDS must be an integer in 1..60" >&2
  exit 2
fi
if [[ ! "${context_window_tokens}" =~ ^[0-9]+$ ]] || (( context_window_tokens < 1 )); then
  echo "SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS must be a positive integer" >&2
  exit 2
fi
if [[ ! "${max_output_tokens}" =~ ^[0-9]+$ ]] || (( max_output_tokens < 1 )); then
  echo "SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS must be a positive integer" >&2
  exit 2
fi
if [[ ! "${transport_margin_tokens}" =~ ^[0-9]+$ ]]; then
  echo "SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS must be a non-negative integer" >&2
  exit 2
fi
if [[ "${source_build}" != "true" && "${source_build}" != "false" ]]; then
  echo "R5_SOURCE_BUILD must be true or false" >&2
  exit 2
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker with the Compose plugin is required on the runtime host" >&2
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required on the runtime host for the LM Studio and authenticated-session preflights" >&2
  exit 2
fi

# Export the narrowly scoped Compose projection so the token is not placed in
# the command's argv. The container still receives it as required by the adapter.
export R5_PORT="${port}"
export R5_BIND_ADDRESS="${bind_address}"
export SPECGRAPH_LOCAL_BASE_URL="${base_url}"
export SPECGRAPH_LOCAL_MODEL="${model}"
export SPECGRAPH_LOCAL_API_KEY=""
export SPECGRAPH_R5_PROJECTED_LOCAL_API_KEY="${local_api_key}"
export SPECGRAPH_LOCAL_TIMEOUT="${SPECGRAPH_LOCAL_TIMEOUT:-60s}"
export SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS="${context_window_tokens}"
export SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS="${max_output_tokens}"
export SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS="${transport_margin_tokens}"
export OPENAI_API_KEY=""
startup=(up -d --wait)
if [[ "${source_build}" == "true" ]]; then
  startup+=(--build)
else
  startup+=(--no-build --pull always)
fi
if ! "${compose[@]}" "${startup[@]}"; then
  echo "R5 Compose startup failed; the partial R5 stack has been stopped" >&2
  cleanup_failed_start
  exit 1
fi
stack_requires_cleanup=true

umask 077
temp_dir="$(mktemp -d)"
models_url="${base_url%/}/models"
lm_header_file="${temp_dir}/lm-studio-header.txt"
lm_curl=(curl --silent --show-error --fail --max-time "${preflight_timeout}")
if [[ -n "${local_api_key}" ]]; then
  if [[ "${local_api_key}" == *$'\n'* || "${local_api_key}" == *$'\r'* ]]; then
    echo "SPECGRAPH_LOCAL_API_KEY must be a single-line value" >&2
    exit 2
  fi
  printf 'Authorization: Bearer %s\n' "${local_api_key}" >"${lm_header_file}"
  lm_curl+=(--header "@${lm_header_file}")
fi
if ! models_json="$("${lm_curl[@]}" "${models_url}")"; then
  echo "LM Studio preflight failed at ${models_url}; the R5 stack has been stopped" >&2
  exit 1
fi
models_json_compact="$(printf '%s' "${models_json}" | tr -d '[:space:]')"
if [[ "${models_json_compact}" != *"\"id\":\"${model}\""* ]]; then
  echo "LM Studio preflight did not expose configured model ${model}; the R5 stack has been stopped" >&2
  exit 1
fi

cookie_jar="${temp_dir}/cookies.txt"
app_probe_host="${R5_APP_PROBE_HOST:-${bind_address}}"
if [[ "${app_probe_host}" == "0.0.0.0" ]]; then
  app_probe_host="127.0.0.1"
elif [[ "${app_probe_host}" == "::" || "${app_probe_host}" == "[::]" ]]; then
  app_probe_host="::1"
fi
if [[ "${app_probe_host}" == *:* && "${app_probe_host}" != \[*\] ]]; then
  app_probe_host="[${app_probe_host}]"
fi
app_url="http://${app_probe_host}:${port}"
if ! session_json="$(curl --silent --show-error --fail \
  --max-time "${preflight_timeout}" \
  --cookie-jar "${cookie_jar}" "${app_url}/api/session")"; then
  echo "R5 unauthenticated-session transport preflight failed; the R5 stack has been stopped" >&2
  exit 1
fi
csrf_token="$(printf '%s' "${session_json}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [[ "${session_json}" != *'"state":"UNAUTHENTICATED"'* || -z "${csrf_token}" ]]; then
  echo "R5 authenticated-session preflight did not return the bounded unauthenticated session contract" >&2
  exit 1
fi

if ! login_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --max-time "${preflight_timeout}" \
  --cookie "${cookie_jar}" --cookie-jar "${cookie_jar}" \
  --header "X-CSRF-TOKEN: ${csrf_token}" \
  --data-urlencode 'username=operator-alpha' \
  --data-urlencode 'password=alpha-demo-2026' \
  "${app_url}/api/session/login")"; then
  echo "R5 login transport preflight failed; the R5 stack has been stopped" >&2
  exit 1
fi
if [[ "${login_status}" != "204" ]]; then
  echo "R5 authenticated-session preflight login failed with HTTP ${login_status}" >&2
  exit 1
fi

if ! authenticated_session="$(curl --silent --show-error --fail \
  --max-time "${preflight_timeout}" \
  --cookie "${cookie_jar}" "${app_url}/api/session")"; then
  echo "R5 authenticated-session transport preflight failed; the R5 stack has been stopped" >&2
  exit 1
fi
authenticated_csrf_token="$(printf '%s' "${authenticated_session}" \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [[ "${authenticated_session}" != *'"state":"AUTHENTICATED"'* \
      || "${authenticated_session}" != *'"operatorId":"operator-alpha"'* \
      || -z "${authenticated_csrf_token}" ]]; then
  echo "R5 authenticated-session preflight did not retain the demo operator identity" >&2
  exit 1
fi

customer_id="44444444-4444-4444-4444-444444444444"
if ! analysis_json="$(curl --silent --show-error --fail \
  --max-time "${analysis_timeout}" \
  --cookie "${cookie_jar}" \
  --header "X-CSRF-TOKEN: ${authenticated_csrf_token}" \
  --request POST \
  "${app_url}/api/customers/${customer_id}/analyses")"; then
  echo "R5 end-to-end analysis failed for seed customer ${customer_id}; the R5 stack has been stopped" >&2
  exit 1
fi

analysis_json_compact="$(printf '%s' "${analysis_json}" | tr -d '\r\n\t')"
json_string_value() {
  local key="${1//./\\.}"
  printf '%s' "${analysis_json_compact}" \
    | sed -n "s/.*\"${key}\":\"\([^\"]*\)\".*/\1/p" \
    | tail -n 1
}

response_model="$(json_string_value modelIdentity)"
response_prompt="$(json_string_value promptIdentity)"
response_runtime="$(json_string_value runtime)"
response_token_estimator="$(json_string_value request.tokenEstimator)"
if [[ "${analysis_json_compact}" != *'"modelProvenance":{'*'"backendIdentity":"local"'*'"metadata":{'*'"externalTransmission":"false"'* ]]; then
  echo "R5 end-to-end analysis omitted local/non-external model provenance; the R5 stack has been stopped" >&2
  exit 1
fi
if [[ "${response_model}" != "${model}" \
      || "${response_prompt}" != "grounded-analysis-v2" \
      || "${response_runtime}" != "lmstudio/llama.cpp" ]]; then
  echo "R5 end-to-end analysis returned unexpected model, prompt, or runtime identity; the R5 stack has been stopped" >&2
  exit 1
fi

required_budget_facts=(
  "\"request.contextWindowTokens\":\"${context_window_tokens}\""
  "\"request.maxOutputTokens\":\"${max_output_tokens}\""
  "\"request.transportMarginTokens\":\"${transport_margin_tokens}\""
  '"kind":"POLICY_RETRIEVAL"'
)
for required_fact in "${required_budget_facts[@]}"; do
  if [[ "${analysis_json_compact}" != *"${required_fact}"* ]]; then
    echo "R5 end-to-end analysis omitted required bounded-grounding provenance ${required_fact}; the R5 stack has been stopped" >&2
    exit 1
  fi
done

estimated_system_tokens="$(json_string_value request.estimatedSystemTokens)"
estimated_user_tokens="$(json_string_value request.estimatedUserTokens)"
estimated_schema_tokens="$(json_string_value request.estimatedSchemaTokens)"
estimated_total_tokens="$(json_string_value request.estimatedTotalTokens)"
for estimate in "${estimated_system_tokens}" "${estimated_user_tokens}" \
    "${estimated_schema_tokens}" "${estimated_total_tokens}"; do
  if [[ ! "${estimate}" =~ ^[0-9]+$ ]] || (( estimate > context_window_tokens )); then
    echo "R5 end-to-end analysis returned a missing or out-of-bounds request token estimate; the R5 stack has been stopped" >&2
    exit 1
  fi
done
estimated_input_tokens=$((estimated_system_tokens + estimated_user_tokens + estimated_schema_tokens))
expected_total_tokens=$((estimated_input_tokens + transport_margin_tokens + max_output_tokens))
if [[ "${response_token_estimator}" != "cl100k-plus-25-percent" \
      || "${estimated_total_tokens}" -ne "${expected_total_tokens}" \
      || "${estimated_total_tokens}" -gt "${context_window_tokens}" ]]; then
  echo "R5 end-to-end analysis returned inconsistent bounded request provenance; the R5 stack has been stopped" >&2
  exit 1
fi

required_detector_facts=(
  '"detectorIdentity":"beta-binomial-review-elevation-v1"'
  '"detectorIdentity":"graded-review-fuzzy-v1"'
  '"detectorIdentity":"random-forest-review-v1"'
)
for required_fact in "${required_detector_facts[@]}"; do
  if [[ "${analysis_json_compact}" != *"${required_fact}"* ]]; then
    echo "R5 end-to-end analysis omitted required provenance ${required_fact}; the R5 stack has been stopped" >&2
    exit 1
  fi
done

bash "${script_dir}/r5-runtime-manifest.sh" \
  "${port}" "${response_model}" "${response_prompt}" "${response_runtime}" \
  "${context_window_tokens}" "${max_output_tokens}" "${transport_margin_tokens}" \
  "${estimated_system_tokens}" "${estimated_user_tokens}" "${estimated_schema_tokens}" \
  "${estimated_input_tokens}" "${estimated_total_tokens}" "${response_token_estimator}" \
  "${bind_address}"
printf 'preflight=lmstudio-models+authenticated-session+seed-analysis+request-budget\n'
printf 'validatedCustomer=%s\n' "${customer_id}"
stack_requires_cleanup=false
