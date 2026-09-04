#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

status=0
if bash "${script_dir}/r4-variant-down.sh" baseline; then
  :
else
  status=$?
fi

if bash "${script_dir}/r4-variant-down.sh" external; then
  :
else
  external_status=$?
  if (( status == 0 )); then
    status="${external_status}"
  fi
fi

exit "${status}"
