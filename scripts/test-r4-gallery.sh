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

echo "R4 gallery lifecycle tests passed"
