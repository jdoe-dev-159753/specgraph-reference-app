#!/usr/bin/env bash
set -euo pipefail

port="${1:-8088}"
model="${2:-ministral-3-8b-instruct-2512}"
prompt_identity="${3:-not-evaluated-by-status}"
runtime_identity="${4:-not-evaluated-by-status}"
context_window_tokens="${5:-4096}"
max_output_tokens="${6:-384}"
transport_margin_tokens="${7:-256}"
estimated_system_tokens="${8:-not-evaluated-by-status}"
estimated_user_tokens="${9:-not-evaluated-by-status}"
estimated_schema_tokens="${10:-not-evaluated-by-status}"
estimated_input_tokens="${11:-not-evaluated-by-status}"
estimated_total_tokens="${12:-not-evaluated-by-status}"
token_estimator="${13:-not-evaluated-by-status}"
publish_address="${14:-127.0.0.1}"

if [[ ! "${port}" =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
  echo "port must be an integer in 1..65535" >&2
  exit 2
fi
if [[ ! "${context_window_tokens}" =~ ^[0-9]+$ ]] || (( context_window_tokens < 1 )); then
  echo "context window must be a positive integer" >&2
  exit 2
fi
if [[ ! "${max_output_tokens}" =~ ^[0-9]+$ ]] || (( max_output_tokens < 1 )); then
  echo "max output must be a positive integer" >&2
  exit 2
fi
if [[ ! "${transport_margin_tokens}" =~ ^[0-9]+$ ]]; then
  echo "transport margin must be a non-negative integer" >&2
  exit 2
fi
for identity in "${model}" "${prompt_identity}" "${runtime_identity}" "${token_estimator}"; do
  if [[ -z "${identity}" || "${identity}" == *$'\n'* || "${identity}" == *$'\r'* ]]; then
    echo "manifest identities must be non-empty single-line values" >&2
    exit 2
  fi
done
if [[ -z "${publish_address}" || "${publish_address}" == *[[:space:]/]* ]]; then
  echo "publish address must be a non-empty host address without whitespace or '/'" >&2
  exit 2
fi
url_host="${publish_address}"
if [[ "${url_host}" == "0.0.0.0" ]]; then
  url_host="127.0.0.1"
elif [[ "${url_host}" == "::" || "${url_host}" == "[::]" ]]; then
  url_host="::1"
fi
if [[ "${url_host}" == *:* && "${url_host}" != \[*\] ]]; then
  url_host="[${url_host}]"
fi
if [[ "${estimated_total_tokens}" != "not-evaluated-by-status" ]]; then
  for estimate in "${estimated_system_tokens}" "${estimated_user_tokens}" \
      "${estimated_schema_tokens}" "${estimated_input_tokens}" "${estimated_total_tokens}"; do
    if [[ ! "${estimate}" =~ ^[0-9]+$ ]]; then
      echo "request token estimates must be non-negative integers" >&2
      exit 2
    fi
  done
  if (( estimated_system_tokens + estimated_user_tokens + estimated_schema_tokens != estimated_input_tokens )); then
    echo "estimated input must equal system + user + schema" >&2
    exit 2
  fi
  if (( estimated_input_tokens + transport_margin_tokens + max_output_tokens != estimated_total_tokens )); then
    echo "estimated total must equal input + transport margin + max output" >&2
    exit 2
  fi
  if (( estimated_total_tokens > context_window_tokens )); then
    echo "estimated total must fit within the configured context window" >&2
    exit 2
  fi
fi

cat <<EOF
R5 runtime ready
url=http://${url_host}:${port}/
bindAddress=${publish_address}
port=${port}
ring=R5
composeProject=specgraph-r5
authentication=session-backed-r4-operator
sessionCookieName=specgraph-r5_session
stage1Detectors=BAYESIAN,FUZZY,RANDOM_FOREST
stage1Composition=ordered-heterogeneous-evidence
stage1Semantics=not-a-calibrated-fused-probability
stage2Grounding=postgresql-pgvector/local-all-MiniLM-L6-v2
stage3Backend=local
stage3Runtime=${runtime_identity}
stage3Model=${model}
stage3PromptIdentity=${prompt_identity}
request.contextWindowTokens=${context_window_tokens}
request.maxOutputTokens=${max_output_tokens}
request.transportMarginTokens=${transport_margin_tokens}
request.estimatedSystemTokens=${estimated_system_tokens}
request.estimatedUserTokens=${estimated_user_tokens}
request.estimatedSchemaTokens=${estimated_schema_tokens}
request.estimatedInputTokens=${estimated_input_tokens}
request.estimatedTotalTokens=${estimated_total_tokens}
request.tokenEstimator=${token_estimator}
request.tokenEstimateSemantics=conservative-approximation-not-exact-ministral-tokenizer
externalTransmission=false
evidenceBoundary=synthetic-review-fixtures-no-aml-performance-claim
EOF
