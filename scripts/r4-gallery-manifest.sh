#!/usr/bin/env bash
set -euo pipefail

bash scripts/r4-variant-manifest.sh baseline 8084 deterministic
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  printf '\n'
  bash scripts/r4-variant-manifest.sh external 8087 openai
fi
