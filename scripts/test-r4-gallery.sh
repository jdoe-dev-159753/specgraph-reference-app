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
ambient_credential_state=absent
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  ambient_credential_state=present
fi
projected_credential_state=absent
if [[ -n "${SPECGRAPH_OPENAI_API_KEY:-}" ]]; then
  projected_credential_state=present
fi
printf 'ambient-credential=%s projected-credential=%s %s\n' \
  "${ambient_credential_state}" "${projected_credential_state}" "$*" >> "${R4_TEST_DOCKER_LOG}"
if [[ "${R4_TEST_FAIL_BASELINE:-false}" == "true" && "$*" == *"specgraph-r4-baseline"* && "$*" == *" up "* ]]; then
  exit 99
fi
if [[ "${R4_TEST_FAIL_BASELINE_DOWN:-false}" == "true" && "$*" == *"specgraph-r4-baseline"* && "$*" == *" down "* ]]; then
  exit 98
fi
if [[ "${R4_TEST_FAIL_EXTERNAL_DOWN:-false}" == "true" && "$*" == *"specgraph-r4-external"* && "$*" == *" down "* ]]; then
  exit 97
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

: > "${docker_log}"
PATH="${temp_dir}/bin:${PATH}" \
R4_TEST_DOCKER_LOG="${docker_log}" \
OPENAI_API_KEY=test-only-key \
bash "${script_dir}/r4-gallery-up.sh" \
  > "${temp_dir}/keyed-stdout" 2> "${temp_dir}/keyed-stderr"
baseline_up="$(grep -F "specgraph-r4-baseline" "${docker_log}" | grep -F " up ")"
external_up="$(grep -F "specgraph-r4-external" "${docker_log}" | grep -F " up ")"
if [[ "${baseline_up}" != "ambient-credential=absent projected-credential=absent"* ]]; then
  echo "deterministic baseline inherited the OpenAI credential" >&2
  exit 1
fi
if [[ "${external_up}" != "ambient-credential=absent projected-credential=present"* ]]; then
  echo "OpenAI variant did not receive its deliberate credential" >&2
  exit 1
fi

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R4_TEST_DOCKER_LOG="${docker_log}" \
  R4_TEST_FAIL_BASELINE_DOWN=true \
  bash "${script_dir}/r4-gallery-down.sh"; then
  echo "failing baseline teardown unexpectedly succeeded" >&2
  exit 1
fi
grep -Fq "specgraph-r4-baseline" "${docker_log}"
grep -Fq "specgraph-r4-external" "${docker_log}"

: > "${docker_log}"
if PATH="${temp_dir}/bin:${PATH}" \
  R4_TEST_DOCKER_LOG="${docker_log}" \
  R4_TEST_FAIL_EXTERNAL_DOWN=true \
  bash "${script_dir}/r4-gallery-down.sh"; then
  echo "failing external teardown unexpectedly succeeded" >&2
  exit 1
fi
grep -Fq "specgraph-r4-baseline" "${docker_log}"
grep -Fq "specgraph-r4-external" "${docker_log}"

echo "R4 gallery lifecycle tests passed"
