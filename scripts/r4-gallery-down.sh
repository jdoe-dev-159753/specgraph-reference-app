#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

bash "${script_dir}/r4-variant-down.sh" baseline
bash "${script_dir}/r4-variant-down.sh" external || true
