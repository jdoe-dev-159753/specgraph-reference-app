#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# Baseline is always available. OpenAI joins the gallery only when a credential is deliberately present.
bash "${script_dir}/r4-variant-up.sh" baseline 8084 deterministic

if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  bash "${script_dir}/r4-variant-up.sh" external 8087 openai
else
  echo "OPENAI_API_KEY is not set; skipping the optional external R4 variant." >&2
fi

printf '\nR4 gallery manifest\n'
bash "${script_dir}/r4-variant-manifest.sh" baseline 8084 deterministic
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  printf '\n'
  bash "${script_dir}/r4-variant-manifest.sh" external 8087 openai
fi
