#!/usr/bin/env bash
set -euo pipefail

port="${1:-8088}"
model="${2:-ministral-3-8b-instruct-2512}"
context_window_tokens="${3:-4096}"
max_output_tokens="${4:-512}"
transport_margin_tokens="${5:-256}"
estimated_total_tokens="${6:-not-evaluated-by-status}"

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
if [[ "${estimated_total_tokens}" != "not-evaluated-by-status" ]]; then
  if [[ ! "${estimated_total_tokens}" =~ ^[0-9]+$ ]] \
      || (( estimated_total_tokens > context_window_tokens )); then
    echo "estimated total must be an integer within the configured context window" >&2
    exit 2
  fi
fi

cat <<EOF
R5 runtime ready
url=http://localhost:${port}/
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
stage3Runtime=lmstudio/llama.cpp
stage3Model=${model}
request.contextWindowTokens=${context_window_tokens}
request.maxOutputTokens=${max_output_tokens}
request.transportMarginTokens=${transport_margin_tokens}
request.estimatedTotalTokens=${estimated_total_tokens}
request.tokenEstimator=cl100k-plus-25-percent
request.tokenEstimateSemantics=conservative-approximation-not-exact-ministral-tokenizer
externalTransmission=false
evidenceBoundary=synthetic-review-fixtures-no-aml-performance-claim
EOF
