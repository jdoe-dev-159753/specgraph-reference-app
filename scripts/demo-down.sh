#!/bin/sh
# Stops only the selected archived demonstrator topology, mirroring demo-up.sh ownership boundaries.
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

case "$mode" in
  both)
    docker compose down --remove-orphans
    ;;
  r0|r1)
    docker compose stop "$mode"
    docker compose rm -f "$mode"
    ;;
esac
