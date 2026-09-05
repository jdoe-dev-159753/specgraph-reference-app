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
printf 'local-key=%s projected-key=%s openai-key=%s bind=%s detectors=%s,%s,%s budget=%s,%s,%s %s\n' \
  "${SPECGRAPH_LOCAL_API_KEY:+present}" \
  "${SPECGRAPH_R5_PROJECTED_LOCAL_API_KEY:+present}" \
  "${OPENAI_API_KEY:+present}" \
  "${R5_BIND_ADDRESS:-}" \
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
  printf 'SPECGRAPH_LOCAL_BASE_URL=%s\n' "${R5_TEST_BASE_URL:-http://192.168.1.20:1234/v1}"
  printf 'SPECGRAPH_LOCAL_API_KEY=%s\n' "${R5_TEST_LOCAL_API_KEY:-test-only-key}"
  printf 'SERVER_SERVLET_SESSION_COOKIE_NAME=%s\n' "${R5_TEST_SESSION_COOKIE:-specgraph-r5_session}"
  printf 'SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS=%s\n' "${R5_TEST_CONTEXT_WINDOW_TOKENS:-4096}"
  printf 'SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS=%s\n' "${R5_TEST_MAX_OUTPUT_TOKENS:-512}"
  printf 'SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS=%s\n' "${R5_TEST_TRANSPORT_MARGIN_TOKENS:-256}"
elif [[ "$*" == *"port r5-container 8080/tcp"* ]]; then
  printf '%s:%s\n' "${R5_TEST_PUBLISHED_ADDRESS:-127.0.0.1}" "${R5_TEST_PORT:-8088}"
fi
EOF

cat > "${temp_dir}/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${R5_TEST_CURL_LOG}"
if [[ "$*" == *"/models"* ]]; then
  [[ "${R5_TEST_FAIL_STAGE:-}" != "model" ]]
  printf '{"data":[{"id":"%s"}]}' "${R5_TEST_MODELS_ID:-test-model}"
elif [[ "$*" == *"/api/customers/44444444-4444-4444-4444-444444444444/analyses"* ]]; then
  [[ "${R5_TEST_FAIL_STAGE:-}" != "analysis" ]]
  estimator="cl100k-plus-25-percent"
  model="test-model"
  prompt="grounded-analysis-v2"
  runtime="lmstudio/llama.cpp"
  estimated_system="250"
  estimated_user="1900"
  estimated_schema="154"
  estimated_total="3072"
  case "${R5_TEST_INVALID_PROVENANCE:-}" in
    model) model="other-model" ;;
    prompt) prompt="unexpected-prompt" ;;
    runtime) runtime="unexpected-runtime" ;;
    estimator) estimator="unexpected-estimator" ;;
    components) estimated_schema="153" ;;
    bounds) estimated_user="5000" ;;
  esac
  printf '%s' '{"modelProvenance":{"backendIdentity":"local","modelIdentity":"'
  printf '%s' "${model}"
  printf '%s' '","promptIdentity":"'
  printf '%s' "${prompt}"
  printf '%s' '","evidenceReferences":[{"kind":"POLICY_RETRIEVAL","evidenceIdentity":"synthetic-policy:test"}],"metadata":{"runtime":"'
  printf '%s' "${runtime}"
  printf '%s' '","externalTransmission":"false","request.contextWindowTokens":"4096","request.maxOutputTokens":"512","request.transportMarginTokens":"256","request.estimatedSystemTokens":"'
  printf '%s' "${estimated_system}"
  printf '%s' '","request.estimatedUserTokens":"'
  printf '%s' "${estimated_user}"
  printf '%s' '","request.estimatedSchemaTokens":"'
  printf '%s' "${estimated_schema}"
  printf '%s' '","request.estimatedTotalTokens":"'
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
  [[ "${R5_TEST_FAIL_STAGE:-}" != "login" ]]
  printf '204'
elif [[ "$*" == *"--cookie "* && "$*" == *"/api/session"* ]]; then
  [[ "${R5_TEST_FAIL_STAGE:-}" != "authenticated-session" ]]
  printf '{"state":"AUTHENTICATED","operatorId":"operator-alpha","csrf":{"token":"analysis-csrf"}}'
else
  [[ "${R5_TEST_FAIL_STAGE:-}" != "initial-session" ]]
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
if [[ "${up_line}" != "local-key= projected-key=present openai-key= bind=127.0.0.1 detectors=,, budget=4096,512,256 "* ]]; then
  echo "R5 launcher leaked an ambient provider credential or lost its deliberate projection" >&2
  exit 1
fi
grep -Fq 'compose -p specgraph-r5' "${docker_log}"
grep -Fq '/models' "${curl_log}"
grep -Fq '/api/session/login' "${curl_log}"
grep -Fq '/api/customers/44444444-4444-4444-4444-444444444444/analyses' "${curl_log}"
if grep -Fq 'test-only-key' "${curl_log}"; then
  echo "R5 launcher exposed the LM Studio token in curl argv" >&2
  exit 1
fi
grep -F '/models' "${curl_log}" | grep -Fq -- '--max-time 10'
grep -F '/models' "${curl_log}" | grep -Fq -- '--header @'
grep -F '/api/session' "${curl_log}" | grep -Fv '/login' | while IFS= read -r session_call; do
  [[ "${session_call}" == *'--max-time 10'* ]]
done
grep -F '/api/session/login' "${curl_log}" | grep -Fq -- '--max-time 10'
grep -F '/api/customers/44444444-4444-4444-4444-444444444444/analyses' "${curl_log}" \
  | grep -Fq -- '--max-time 90'
grep -F '/api/customers/44444444-4444-4444-4444-444444444444/analyses' "${curl_log}" \
  | grep -Fq -- 'X-CSRF-TOKEN: analysis-csrf'
grep -Fq 'stage1Detectors=BAYESIAN,FUZZY,RANDOM_FOREST' "${temp_dir}/stdout"
grep -Fq 'url=http://127.0.0.1:8088/' "${temp_dir}/stdout"
grep -Fq 'bindAddress=127.0.0.1' "${temp_dir}/stdout"
grep -Fq 'stage1Semantics=not-a-calibrated-fused-probability' "${temp_dir}/stdout"
grep -Fq 'stage3Backend=local' "${temp_dir}/stdout"
grep -Fq 'stage3Runtime=lmstudio/llama.cpp' "${temp_dir}/stdout"
grep -Fq 'stage3Model=test-model' "${temp_dir}/stdout"
grep -Fq 'stage3PromptIdentity=grounded-analysis-v2' "${temp_dir}/stdout"
grep -Fq 'request.contextWindowTokens=4096' "${temp_dir}/stdout"
grep -Fq 'request.maxOutputTokens=512' "${temp_dir}/stdout"
grep -Fq 'request.transportMarginTokens=256' "${temp_dir}/stdout"
grep -Fq 'request.estimatedSystemTokens=250' "${temp_dir}/stdout"
grep -Fq 'request.estimatedUserTokens=1900' "${temp_dir}/stdout"
grep -Fq 'request.estimatedSchemaTokens=154' "${temp_dir}/stdout"
grep -Fq 'request.estimatedInputTokens=2304' "${temp_dir}/stdout"
grep -Fq 'request.estimatedTotalTokens=3072' "${temp_dir}/stdout"
grep -Fq 'request.tokenEstimator=cl100k-plus-25-percent' "${temp_dir}/stdout"
grep -Fq 'externalTransmission=false' "${temp_dir}/stdout"
grep -Fq 'preflight=lmstudio-models+authenticated-session+seed-analysis+request-budget' "${temp_dir}/stdout"
grep -Fq 'validatedCustomer=44444444-4444-4444-4444-444444444444' "${temp_dir}/stdout"

PATH="${temp_dir}/bin:${PATH}" \
R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
SPECGRAPH_LOCAL_MODEL=test-model \
R5_BIND_ADDRESS=192.168.1.44 \
bash "${script_dir}/r5-runtime-up.sh" > "${temp_dir}/private-bind-stdout"
grep -Fq 'url=http://192.168.1.44:8088/' "${temp_dir}/private-bind-stdout"
grep -Fq 'bindAddress=192.168.1.44' "${temp_dir}/private-bind-stdout"

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

for failed_stage in model initial-session login authenticated-session analysis; do
  : > "${docker_log}"
  if PATH="${temp_dir}/bin:${PATH}" \
    R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
    R5_TEST_FAIL_STAGE="${failed_stage}" \
    SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
    SPECGRAPH_LOCAL_MODEL=test-model \
    bash "${script_dir}/r5-runtime-up.sh" > /dev/null 2> "${temp_dir}/${failed_stage}-stderr"; then
    echo "R5 launcher ignored the ${failed_stage} transport failure" >&2
    exit 1
  fi
  grep -Fq ' down --remove-orphans' "${docker_log}"
done

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_MODELS_ID=other-model \
  SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
  SPECGRAPH_LOCAL_MODEL=test-model \
  bash "${script_dir}/r5-runtime-up.sh" > /dev/null 2> "${temp_dir}/missing-model-stderr"; then
  echo "R5 launcher accepted an LM Studio catalogue without the configured model" >&2
  exit 1
fi
grep -Fq ' down --remove-orphans' "${docker_log}"
grep -Fq 'did not expose configured model' "${temp_dir}/missing-model-stderr"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_INVALID_ANALYSIS=true \
  SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
  SPECGRAPH_LOCAL_MODEL=test-model \
  bash "${script_dir}/r5-runtime-up.sh" > /dev/null 2> "${temp_dir}/invalid-analysis-stderr"; then
  echo "R5 launcher accepted incomplete end-to-end provenance" >&2
  exit 1
fi
grep -Fq ' down --remove-orphans' "${docker_log}"
grep -Fq 'random-forest-review-v1' "${temp_dir}/invalid-analysis-stderr"

for invalid_provenance in model prompt runtime estimator components bounds; do
  : > "${docker_log}"
  if PATH="${temp_dir}/bin:${PATH}" \
    R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
    R5_TEST_INVALID_PROVENANCE="${invalid_provenance}" \
    SPECGRAPH_LOCAL_BASE_URL=http://192.168.1.20:1234/v1 \
    SPECGRAPH_LOCAL_MODEL=test-model \
    bash "${script_dir}/r5-runtime-up.sh" > /dev/null \
      2> "${temp_dir}/invalid-${invalid_provenance}-stderr"; then
    echo "R5 launcher accepted invalid ${invalid_provenance} provenance" >&2
    exit 1
  fi
  grep -Fq ' down --remove-orphans' "${docker_log}"
done

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
grep -Fq 'url=http://127.0.0.1:8088/' "${temp_dir}/healthy-status"
grep -Fq 'runtimeState=healthy' "${temp_dir}/healthy-status"
grep -Fq 'stage3Model=test-model' "${temp_dir}/healthy-status"
grep -Fq 'sessionCookieName=specgraph-r5_session' "${temp_dir}/healthy-status"
grep -Fq 'request.contextWindowTokens=4096' "${temp_dir}/healthy-status"
grep -Fq 'request.maxOutputTokens=512' "${temp_dir}/healthy-status"
grep -Fq 'request.transportMarginTokens=256' "${temp_dir}/healthy-status"
grep -Fq 'request.estimatedSystemTokens=not-evaluated-by-status' "${temp_dir}/healthy-status"
grep -Fq 'request.estimatedInputTokens=not-evaluated-by-status' "${temp_dir}/healthy-status"
grep -Fq 'request.estimatedTotalTokens=not-evaluated-by-status' "${temp_dir}/healthy-status"
grep -Fq 'lmStudioState=ready' "${temp_dir}/healthy-status"
grep -F '/models' "${curl_log}" | tail -n 1 | grep -Fq -- '--max-time 10'
if grep -Fq 'test-only-key' "${curl_log}"; then
  echo "R5 status exposed the LM Studio token in curl argv" >&2
  exit 1
fi

PATH="${temp_dir}/bin:${PATH}" \
R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
R5_TEST_CONTAINER=r5-container R5_TEST_PUBLISHED_ADDRESS=192.168.1.44 \
bash "${script_dir}/r5-runtime-status.sh" > "${temp_dir}/private-bind-status"
grep -Fq 'url=http://192.168.1.44:8088/' "${temp_dir}/private-bind-status"
grep -Fq 'bindAddress=192.168.1.44' "${temp_dir}/private-bind-status"

if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_CONTAINER=r5-container R5_TEST_FAIL_STAGE=model \
  bash "${script_dir}/r5-runtime-status.sh" > "${temp_dir}/lm-down-status"; then
  echo "R5 status reported success while LM Studio was unreachable" >&2
  exit 1
fi
grep -Fq 'lmStudioState=unreachable' "${temp_dir}/lm-down-status"
if grep -Fq 'R5 runtime ready' "${temp_dir}/lm-down-status"; then
  echo "R5 status advertised an LM-disconnected runtime as ready" >&2
  exit 1
fi

if PATH="${temp_dir}/bin:${PATH}" \
  R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
  R5_TEST_CONTAINER=r5-container R5_TEST_MODELS_ID=other-model \
  bash "${script_dir}/r5-runtime-status.sh" > "${temp_dir}/lm-model-status"; then
  echo "R5 status accepted an unavailable configured model" >&2
  exit 1
fi
grep -Fq 'lmStudioState=configured-model-unavailable' "${temp_dir}/lm-model-status"

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

if bash "${script_dir}/r5-runtime-manifest.sh" \
  8088 test-model grounded-analysis-v2 lmstudio/llama.cpp \
  4096 512 256 250 1900 154 2303 3071 cl100k-plus-25-percent \
  > /dev/null 2> "${temp_dir}/invalid-manifest-stderr"; then
  echo "R5 manifest accepted a non-recomposable request budget" >&2
  exit 1
fi
grep -Fq 'estimated input must equal system + user + schema' "${temp_dir}/invalid-manifest-stderr"

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
bash "${script_dir}/r5-runtime-down.sh" > "${temp_dir}/down-retain"
grep -Fq ' down --remove-orphans' "${docker_log}"
if grep -Fq ' down -v --remove-orphans' "${docker_log}"; then
  echo "R5 normal shutdown unexpectedly purged the embedding cache" >&2
  exit 1
fi
grep -Fq 'embeddingCache=retained' "${temp_dir}/down-retain"

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R5_TEST_DOCKER_LOG="${docker_log}" R5_TEST_CURL_LOG="${curl_log}" \
bash "${script_dir}/r5-runtime-down.sh" --purge > "${temp_dir}/down-purge"
grep -Fq ' down -v --remove-orphans' "${docker_log}"
grep -Fq 'embeddingCache=purged' "${temp_dir}/down-purge"

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
grep -Fq -- '- "${R5_BIND_ADDRESS:-127.0.0.1}:${R5_PORT:-8088}:8080"' \
  "${repo_root}/compose.r5.yaml"
grep -Fq -- '- r5-embedding-cache:/tmp/specgraph-embedding-model' \
  "${repo_root}/compose.r5.yaml"
grep -Fq 'r5-cache-init:' "${repo_root}/compose.r5.yaml"
grep -Fq 'command: ["sh", "-c", "chown -R 10001:10001 /cache"]' \
  "${repo_root}/compose.r5.yaml"
grep -Fq 'condition: service_completed_successfully' "${repo_root}/compose.r5.yaml"
grep -Fq 'r5-embedding-cache:' "${repo_root}/compose.r5.yaml"
grep -Fq 'response_model="$(json_string_value modelIdentity)"' "${script_dir}/r5-runtime-up.sh"
grep -Fq 'response_prompt="$(json_string_value promptIdentity)"' "${script_dir}/r5-runtime-up.sh"
grep -Fq 'response_runtime="$(json_string_value runtime)"' "${script_dir}/r5-runtime-up.sh"

echo "R5 runtime launcher contract tests passed"
