#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

mkdir -p "${temp_dir}/bin"
docker_log="${temp_dir}/docker.log"
cat > "${temp_dir}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${R4_TEST_DOCKER_LOG}"
if [[ "${R4_TEST_FAIL_BASELINE:-false}" == "true" && "$*" == *"specgraph-r4-baseline"* && "$*" == *" up "* ]]; then
  exit 99
fi
EOF
chmod +x "${temp_dir}/bin/docker"

PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
env -u OPENAI_API_KEY bash "${script_dir}/r4-gallery-up.sh" \
  > "${temp_dir}/stdout" 2> "${temp_dir}/stderr"

grep -Fq "compose -p specgraph-r4-external" "${docker_log}"
grep -Fq "down --remove-orphans" "${docker_log}"
if grep -F "compose -p specgraph-r4-external" "${docker_log}" | grep -Fq " up "; then
  echo "credential-free gallery unexpectedly started the external variant" >&2
  exit 1
fi
grep -Fq "the optional external R4 variant is stopped" "${temp_dir}/stderr"
if grep -Fq "url=http://localhost:8087/" "${temp_dir}/stdout"; then
  echo "credential-free gallery unexpectedly advertised the external variant" >&2
  exit 1
fi

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R4_TEST_DOCKER_LOG="${docker_log}" \
  R4_TEST_FAIL_BASELINE=true \
  env -u OPENAI_API_KEY bash "${script_dir}/r4-gallery-up.sh" \
    > "${temp_dir}/failed-stdout" 2> "${temp_dir}/failed-stderr"; then
  echo "injected baseline failure unexpectedly succeeded" >&2
  exit 1
fi
first_command="$(head -n 1 "${docker_log}")"
if [[ "${first_command}" != *"specgraph-r4-external"* || "${first_command}" != *"down --remove-orphans"* ]]; then
  echo "external opt-out did not precede the failing baseline startup" >&2
  exit 1
fi

echo "R4 gallery lifecycle tests passed"
