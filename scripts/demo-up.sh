#!/bin/sh
# Launches the archived R0/R1 demonstrators with explicit mode selection and optional local overrides.
set -eu

mode="${1:-both}"
case "$mode" in
  both|r0|r1) ;;
  *) echo "Usage: $0 [both|r0|r1]" >&2; exit 2 ;;
esac

if [ -f ./.env.demo ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env.demo
  set +a
fi

DEMO_HOST="${DEMO_HOST:-$(hostname -f)}"
R0_PORT="${R0_PORT:-8080}"
R1_PORT="${R1_PORT:-8081}"
R0_URL="${R0_URL:-http://${DEMO_HOST}:${R0_PORT}/}"
R1_URL="${R1_URL:-http://${DEMO_HOST}:${R1_PORT}/}"
export DEMO_HOST R0_PORT R1_PORT R0_URL R1_URL

case "$mode" in
  both) services="r0 r1" ;;
  r0) services="r0" ;;
  r1) services="r1" ;;
esac

printf 'Pulling prebuilt checkpoint image(s): %s\n' "$services"
# shellcheck disable=SC2086
if ! docker compose pull --quiet $services; then
  echo 'Image pull failed. For the private GHCR package, authenticate once with docker login ghcr.io.' >&2
  exit 1
fi

# shellcheck disable=SC2086
docker compose up -d --no-build $services

wait_for_service() {
  service="$1"
  container_id="$(docker compose ps -q "$service")"
  if [ -z "$container_id" ]; then
    docker compose ps
    echo "Compose did not create service $service." >&2
    exit 1
  fi

  attempt=1
  while [ "$attempt" -le 60 ]; do
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null || true)"
    if [ "$health" = healthy ]; then
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 1
  done

  docker compose ps
  docker compose logs --no-color --tail=100 "$service"
  echo "Demo service $service did not become healthy within 60 seconds." >&2
  exit 1
}

case "$mode" in
  both)
    wait_for_service r0
    wait_for_service r1
    printf '\nR0 ready: %s\n' "$R0_URL"
    printf 'R1 ready: %s\n' "$R1_URL"
    printf '\nBoth checkpoints are running side by side. Stop them with ./scripts/demo-down.sh\n\n'
    ;;
  r0)
    wait_for_service r0
    printf '\nR0 ready: %s\n' "$R0_URL"
    printf 'R0 is running in the background. Stop it with ./scripts/demo-down.sh r0\n\n'
    ;;
  r1)
    wait_for_service r1
    printf '\nR1 ready: %s\n' "$R1_URL"
    printf 'R1 is running in the background. Stop it with ./scripts/demo-down.sh r1\n\n'
    ;;
esac
