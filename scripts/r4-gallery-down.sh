#!/usr/bin/env bash
set -euo pipefail

bash scripts/r4-variant-down.sh baseline
bash scripts/r4-variant-down.sh external || true
