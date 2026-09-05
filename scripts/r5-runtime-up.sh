#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
port="${R5_PORT:-8088}"
model="${SPECGRAPH_LOCAL_MODEL:-ministral-3-8b-instruct-2512}"
base_url="${SPECGRAPH_LOCAL_BASE_URL:-}"
local_api_key="${SPECGRAPH_LOCAL_API_KEY:-}"
analysis_timeout="${R5_ANALYSIS_TIMEOUT_SECONDS:-90}"
context_window_tokens="${SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS:-4096}"
max_output_tokens="${SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS:-512}"
transport_margin_tokens="${SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS:-256}"
project="specgraph-r5"
compose=(docker compose -p "${project}" -f "${repo_root}/compose.r5.yaml")

cleanup_failed_start() {
  "${compose[@]}" down --remove-orphans >/dev/null 2>&1 || true
}

if [[ -z "${base_url}" ]]; then
  echo "SPECGRAPH_LOCAL_BASE_URL is required (for example http://192.168.1.20:1234/v1)" >&2
  exit 2
fi
if [[ ! "${port}" =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
  echo "R5_PORT must be an integer in 1..65535" >&2
  exit 2
fi
if [[ ! "${analysis_timeout}" =~ ^[0-9]+$ ]] \
    || (( analysis_timeout < 1 || analysis_timeout > 300 )); then
  echo "R5_ANALYSIS_TIMEOUT_SECONDS must be an integer in 1..300" >&2
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
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker with the Compose plugin is required on the runtime host" >&2
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required on the runtime host for the LM Studio and authenticated-session preflights" >&2
  exit 2
fi

if ! env \
  R5_PORT="${port}" \
  SPECGRAPH_LOCAL_BASE_URL="${base_url}" \
  SPECGRAPH_LOCAL_MODEL="${model}" \
  SPECGRAPH_LOCAL_API_KEY="" \
  SPECGRAPH_R5_PROJECTED_LOCAL_API_KEY="${local_api_key}" \
  SPECGRAPH_LOCAL_TIMEOUT="${SPECGRAPH_LOCAL_TIMEOUT:-60s}" \
  SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS="${context_window_tokens}" \
  SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS="${max_output_tokens}" \
  SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS="${transport_margin_tokens}" \
  OPENAI_API_KEY="" \
  "${compose[@]}" up --build -d --wait; then
  echo "R5 Compose startup failed; the partial R5 stack has been stopped" >&2
  cleanup_failed_start
  exit 1
fi

models_url="${base_url%/}/models"
lm_curl=(curl --silent --show-error --fail --max-time 10)
if [[ -n "${local_api_key}" ]]; then
  lm_curl+=(--header "Authorization: Bearer ${local_api_key}")
fi
if ! "${lm_curl[@]}" "${models_url}" >/dev/null; then
  echo "LM Studio preflight failed at ${models_url}; the R5 stack has been stopped" >&2
  cleanup_failed_start
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
cookie_jar="${temp_dir}/cookies.txt"
app_url="http://127.0.0.1:${port}"
session_json="$(curl --silent --show-error --fail --cookie-jar "${cookie_jar}" "${app_url}/api/session")"
csrf_token="$(printf '%s' "${session_json}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [[ "${session_json}" != *'"state":"UNAUTHENTICATED"'* || -z "${csrf_token}" ]]; then
  echo "R5 authenticated-session preflight did not return the bounded unauthenticated session contract" >&2
  cleanup_failed_start
  exit 1
fi

login_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --cookie "${cookie_jar}" --cookie-jar "${cookie_jar}" \
  --header "X-CSRF-TOKEN: ${csrf_token}" \
  --data-urlencode 'username=operator-alpha' \
  --data-urlencode 'password=alpha-demo-2026' \
  "${app_url}/api/session/login")"
if [[ "${login_status}" != "204" ]]; then
  echo "R5 authenticated-session preflight login failed with HTTP ${login_status}" >&2
  cleanup_failed_start
  exit 1
fi

authenticated_session="$(curl --silent --show-error --fail \
  --cookie "${cookie_jar}" "${app_url}/api/session")"
authenticated_csrf_token="$(printf '%s' "${authenticated_session}" \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [[ "${authenticated_session}" != *'"state":"AUTHENTICATED"'* \
      || "${authenticated_session}" != *'"operatorId":"operator-alpha"'* \
      || -z "${authenticated_csrf_token}" ]]; then
  echo "R5 authenticated-session preflight did not retain the demo operator identity" >&2
  cleanup_failed_start
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
  cleanup_failed_start
  exit 1
fi

if [[ "${analysis_json}" != *'"modelProvenance":{'*'"backendIdentity":"local"'*'"metadata":{'*'"externalTransmission":"false"'* ]]; then
  echo "R5 end-to-end analysis omitted local/non-external model provenance; the R5 stack has been stopped" >&2
  cleanup_failed_start
  exit 1
fi

required_budget_facts=(
  "\"request.contextWindowTokens\":\"${context_window_tokens}\""
  "\"request.maxOutputTokens\":\"${max_output_tokens}\""
  "\"request.transportMarginTokens\":\"${transport_margin_tokens}\""
  '"request.tokenEstimator":"cl100k-plus-25-percent"'
  '"kind":"POLICY_RETRIEVAL"'
)
for required_fact in "${required_budget_facts[@]}"; do
  if [[ "${analysis_json}" != *"${required_fact}"* ]]; then
    echo "R5 end-to-end analysis omitted required bounded-grounding provenance ${required_fact}; the R5 stack has been stopped" >&2
    cleanup_failed_start
    exit 1
  fi
done

estimated_total_tokens="$(printf '%s' "${analysis_json}" \
  | sed -n 's/.*"request\.estimatedTotalTokens":"\([0-9][0-9]*\)".*/\1/p')"
if [[ -z "${estimated_total_tokens}" ]] \
    || (( estimated_total_tokens > context_window_tokens )); then
  echo "R5 end-to-end analysis omitted a bounded request.estimatedTotalTokens value; the R5 stack has been stopped" >&2
  cleanup_failed_start
  exit 1
fi

required_detector_facts=(
  '"detectorIdentity":"beta-binomial-review-elevation-v1"'
  '"detectorIdentity":"graded-review-fuzzy-v1"'
  '"detectorIdentity":"random-forest-review-v1"'
)
for required_fact in "${required_detector_facts[@]}"; do
  if [[ "${analysis_json}" != *"${required_fact}"* ]]; then
    echo "R5 end-to-end analysis omitted required provenance ${required_fact}; the R5 stack has been stopped" >&2
    cleanup_failed_start
    exit 1
  fi
done

bash "${script_dir}/r5-runtime-manifest.sh" \
  "${port}" "${model}" "${context_window_tokens}" "${max_output_tokens}" \
  "${transport_margin_tokens}" "${estimated_total_tokens}"
printf 'preflight=lmstudio-models+authenticated-session+seed-analysis+request-budget\n'
printf 'validatedCustomer=%s\n' "${customer_id}"
