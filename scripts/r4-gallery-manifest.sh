#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

bash "${script_dir}/r4-variant-manifest.sh" baseline 8084 deterministic
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  printf '\n'
  bash "${script_dir}/r4-variant-manifest.sh" external 8087 openai
fi
