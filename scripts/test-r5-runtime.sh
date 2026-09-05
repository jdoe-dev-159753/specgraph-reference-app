#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT
mkdir -p "${temp_dir}/bin"
docker_log="${temp_dir}/docker.log"
curl_log="${temp_dir}/curl.log"

cat > "${temp_dir}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'local-key=%s projected-key=%s openai-key=%s detectors=%s,%s,%s budget=%s,%s,%s %s\n' \
  "${SPECGRAPH_LOCAL_API_KEY:+present}" \
  "${SPECGRAPH_R5_PROJECTED_LOCAL_API_KEY:+present}" \
  "${OPENAI_API_KEY:+present}" \
  "${SPECGRAPH_ANALYSIS_DETECTORS_0:-}" \
  "${SPECGRAPH_ANALYSIS_DETECTORS_1:-}" \
  "${SPECGRAPH_ANALYSIS_DETECTORS_2:-}" \
  "${SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS:-}" \
  "${SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS:-}" \
  "${SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS:-}" \
  "$*" >> "${R5_TEST_DOCKER_LOG}"
if [[ "$*" == *"compose -p specgraph-r5"* \
      && "$*" == *" up --build -d --wait"* \
      && "${R5_TEST_FAIL_COMPOSE:-false}" == "true" ]]; then
  exit 1
elif [[ "$*" == *"compose -p specgraph-r5"* && "$*" == *" ps -q --all r5"* ]]; then
  printf '%s\n' "${R5_TEST_CONTAINER:-}"
elif [[ "$*" == *"inspect --format {{.State.Status}} r5-container"* ]]; then
  printf '%s\n' "${R5_TEST_CONTAINER_STATE:-running}"
elif [[ "$*" == *"inspect --format {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} r5-container"* ]]; then
  printf '%s\n' "${R5_TEST_HEALTH_STATE:-healthy}"
elif [[ "$*" == *"inspect --format {{range .Config.Env}}{{println .}}{{end}} r5-container"* ]]; then
  printf 'SPECGRAPH_ANALYSIS_BACKEND=%s\n' "${R5_TEST_BACKEND:-local}"
  printf 'SPECGRAPH_ANALYSIS_DETECTORS_0=%s\n' "${R5_TEST_DETECTOR_0:-BAYESIAN}"
  printf 'SPECGRAPH_ANALYSIS_DETECTORS_1=%s\n' "${R5_TEST_DETECTOR_1:-FUZZY}"
  printf 'SPECGRAPH_ANALYSIS_DETECTORS_2=%s\n' "${R5_TEST_DETECTOR_2:-RANDOM_FOREST}"
  printf 'SPECGRAPH_LOCAL_MODEL=%s\n' "${R5_TEST_MODEL:-test-model}"
  printf 'SERVER_SERVLET_SESSION_COOKIE_NAME=%s\n' "${R5_TEST_SESSION_COOKIE:-specgraph-r5_session}"
  printf 'SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS=%s\n' "${R5_TEST_CONTEXT_WINDOW_TOKENS:-4096}"
  printf 'SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS=%s\n' "${R5_TEST_MAX_OUTPUT_TOKENS:-512}"
  printf 'SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS=%s\n' "${R5_TEST_TRANSPORT_MARGIN_TOKENS:-256}"
elif [[ "$*" == *"port r5-container 8080/tcp"* ]]; then
  printf '0.0.0.0:%s\n' "${R5_TEST_PORT:-8088}"
fi
EOF

cat > "${temp_dir}/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${R5_TEST_CURL_LOG}"
if [[ "$*" == *"/models"* ]]; then
  [[ "${R5_TEST_FAIL_LM:-false}" != "true" ]]
  printf '{"data":[{"id":"test-model"}]}'
elif [[ "$*" == *"/api/customers/44444444-4444-4444-4444-444444444444/analyses"* ]]; then
  estimator="cl100k-plus-25-percent"
  estimated_total="3072"
  if [[ "${R5_TEST_INVALID_BUDGET:-false}" == "true" ]]; then
    estimator="unexpected-estimator"
    estimated_total="5000"
  fi
  printf '%s' '{"modelProvenance":{"backendIdentity":"local","modelIdentity":"test-model","evidenceReferences":[{"kind":"POLICY_RETRIEVAL","evidenceIdentity":"synthetic-policy:test"}],"metadata":{"externalTransmission":"false","request.contextWindowTokens":"4096","request.maxOutputTokens":"512","request.transportMarginTokens":"256","request.estimatedTotalTokens":"'
  printf '%s' "${estimated_total}"
  printf '%s' '","request.tokenEstimator":"'
  printf '%s' "${estimator}"
  printf '%s' '"}},"detectorProvenance":['
  if [[ "${R5_TEST_INVALID_ANALYSIS:-false}" == "true" ]]; then
    printf '%s' '{"detectorIdentity":"beta-binomial-review-elevation-v1"},'
    printf '%s' '{"detectorIdentity":"graded-review-fuzzy-v1"}]}'
  else
    printf '%s' '{"detectorIdentity":"beta-binomial-review-elevation-v1"},'
    printf '%s' '{"detectorIdentity":"graded-review-fuzzy-v1"},'
    printf '%s' '{"detectorIdentity":"random-forest-review-v1"}]}'
  fi
elif [[ "$*" == *"/api/session/login"* ]]; then
  printf '204'
elif [[ "$*" == *"--cookie "* && "$*" == *"/api/session"* ]]; then
  printf '{"state":"AUTHENTICATED","operatorId":"operator-alpha","csrf":{"token":"analysis-csrf"}}'
else
  printf '{"state":"UNAUTHENTICATED","csrf":{"token":"login-csrf"}}'
fi
EOF
chmod +x "${temp_dir}/bin/docker" "${temp_dir}/bin/curl"

if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  env -u SPECGRAPH_LOCAL_BASE_URL bash "${script_dir}/r5-runtime-up.sh"; then
  echo "R5 launcher accepted a missing LM Studio endpoint" >&2
  exit 1
fi

: > "${docker_log}"
: > "${curl_log}"
PATH="${temp_dir}/bin:${PATH}" \
R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
SPECGRAPH_LOCAL_MODEL=test-model \
SPECGRAPH_LOCAL_API_KEY=test-only-key \
bash "${script_dir}/r5-runtime-up.sh" > "${temp_dir}/stdout"

up_line="$(grep -F ' up --build -d --wait' "${docker_log}")"
if [[ "${up_line}" != "local-key= projected-key=present openai-key= detectors=,, budget=4096,512,256 "* ]]; then
  echo "R5 launcher leaked an ambient provider credential or lost its deliberate projection" >&2
  exit 1
fi
grep -Fq 'compose -p specgraph-r5' "${docker_log}"
grep -Fq '/models' "${curl_log}"
grep -Fq '/api/session/login' "${curl_log}"
grep -Fq '/api/customers/44444444-4444-4444-4444-444444444444/analyses' "${curl_log}"
grep -F '/api/customers/44444444-4444-4444-4444-444444444444/analyses' "${curl_log}" \
  | grep -Fq -- '--max-time 90'
grep -F '/api/customers/44444444-4444-4444-4444-444444444444/analyses' "${curl_log}" \
  | grep -Fq -- 'X-CSRF-TOKEN: analysis-csrf'
grep -Fq 'stage1Detectors=BAYESIAN,FUZZY,RANDOM_FOREST' "${temp_dir}/stdout"
grep -Fq 'stage1Semantics=not-a-calibrated-fused-probability' "${temp_dir}/stdout"
grep -Fq 'stage3Backend=local' "${temp_dir}/stdout"
grep -Fq 'request.contextWindowTokens=4096' "${temp_dir}/stdout"
grep -Fq 'request.maxOutputTokens=512' "${temp_dir}/stdout"
grep -Fq 'request.transportMarginTokens=256' "${temp_dir}/stdout"
grep -Fq 'request.estimatedTotalTokens=3072' "${temp_dir}/stdout"
grep -Fq 'request.tokenEstimator=cl100k-plus-25-percent' "${temp_dir}/stdout"
grep -Fq 'externalTransmission=false' "${temp_dir}/stdout"
grep -Fq 'preflight=lmstudio-models+authenticated-session+seed-analysis+request-budget' "${temp_dir}/stdout"
grep -Fq 'validatedCustomer=44444444-4444-4444-4444-444444444444' "${temp_dir}/stdout"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_FAIL_COMPOSE=true \
  SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
  bash "${script_dir}/r5-runtime-up.sh" > /dev/null 2> "${temp_dir}/compose-failed-stderr"; then
  echo "R5 launcher ignored a failed Compose startup" >&2
  exit 1
fi
grep -Fq ' up --build -d --wait' "${docker_log}"
grep -Fq ' down --remove-orphans' "${docker_log}"
grep -Fq 'partial R5 stack has been stopped' "${temp_dir}/compose-failed-stderr"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_FAIL_LM=true \
  SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
  bash "${script_dir}/r5-runtime-up.sh" > /dev/null 2> "${temp_dir}/failed-stderr"; then
  echo "R5 launcher ignored an unavailable LM Studio endpoint" >&2
  exit 1
fi
grep -Fq ' down --remove-orphans' "${docker_log}"
grep -Fq 'LM Studio preflight failed' "${temp_dir}/failed-stderr"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_INVALID_ANALYSIS=true \
  SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
  bash "${script_dir}/r5-runtime-up.sh" > /dev/null 2> "${temp_dir}/invalid-analysis-stderr"; then
  echo "R5 launcher accepted incomplete end-to-end provenance" >&2
  exit 1
fi
grep -Fq ' down --remove-orphans' "${docker_log}"
grep -Fq 'random-forest-review-v1' "${temp_dir}/invalid-analysis-stderr"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_INVALID_BUDGET=true \
  SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
  bash "${script_dir}/r5-runtime-up.sh" > /dev/null 2> "${temp_dir}/invalid-budget-stderr"; then
  echo "R5 launcher accepted incomplete request-budget provenance" >&2
  exit 1
fi
grep -Fq ' down --remove-orphans' "${docker_log}"
grep -Fq 'request.tokenEstimator' "${temp_dir}/invalid-budget-stderr"

if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  bash "${script_dir}/r5-runtime-status.sh" > "${temp_dir}/stopped-status"; then
  echo "R5 status reported success for a stopped runtime" >&2
  exit 1
fi
grep -Fq 'R5 runtime unavailable' "${temp_dir}/stopped-status"
if grep -Fq 'R5 runtime ready' "${temp_dir}/stopped-status"; then
  echo "R5 status advertised a stopped runtime as ready" >&2
  exit 1
fi

PATH="${temp_dir}/bin:${PATH}" \
R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
R5_TEST_CONTAINER=r5-container \
bash "${script_dir}/r5-runtime-status.sh" > "${temp_dir}/healthy-status"
grep -Fq 'R5 runtime ready' "${temp_dir}/healthy-status"
grep -Fq 'runtimeState=healthy' "${temp_dir}/healthy-status"
grep -Fq 'stage3Model=test-model' "${temp_dir}/healthy-status"
grep -Fq 'sessionCookieName=specgraph-r5_session' "${temp_dir}/healthy-status"
grep -Fq 'request.contextWindowTokens=4096' "${temp_dir}/healthy-status"
grep -Fq 'request.maxOutputTokens=512' "${temp_dir}/healthy-status"
grep -Fq 'request.transportMarginTokens=256' "${temp_dir}/healthy-status"
grep -Fq 'request.estimatedTotalTokens=not-evaluated-by-status' "${temp_dir}/healthy-status"

if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_CONTAINER=r5-container R5_TEST_SESSION_COOKIE=specgraph-r4-default_session \
  bash "${script_dir}/r5-runtime-status.sh" > "${temp_dir}/wrong-cookie-status"; then
  echo "R5 status accepted the shared R4 fallback session cookie" >&2
  exit 1
fi
grep -Fq 'configurationState=unexpected' "${temp_dir}/wrong-cookie-status"

if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_CONTAINER=r5-container R5_TEST_HEALTH_STATE=unhealthy \
  bash "${script_dir}/r5-runtime-status.sh" > "${temp_dir}/unhealthy-status"; then
  echo "R5 status reported success for an unhealthy runtime" >&2
  exit 1
fi
grep -Fq 'healthState=unhealthy' "${temp_dir}/unhealthy-status"
if grep -Fq 'R5 runtime ready' "${temp_dir}/unhealthy-status"; then
  echo "R5 status advertised an unhealthy runtime as ready" >&2
  exit 1
fi

grep -Fq 'SPRING_PROFILES_ACTIVE: r4' "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_ANALYSIS_DETECTORS_0: BAYESIAN' "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_ANALYSIS_DETECTORS_1: FUZZY' "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_ANALYSIS_DETECTORS_2: RANDOM_FOREST' "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_ANALYSIS_BACKEND: local' "${repo_root}/compose.r5.yaml"
grep -Fq 'SERVER_SERVLET_SESSION_COOKIE_NAME: specgraph-r5_session' "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_LOCAL_API_KEY: "${SPECGRAPH_R5_PROJECTED_LOCAL_API_KEY:-}"' "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS: "${SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS:-4096}"' \
  "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS: "${SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS:-512}"' \
  "${repo_root}/compose.r5.yaml"
grep -Fq 'SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS: "${SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS:-256}"' \
  "${repo_root}/compose.r5.yaml"

echo "R5 runtime launcher contract tests passed"
