#!/usr/bin/env bash
set -euo pipefail

builder="${BUILDX_BUILDER:?BUILDX_BUILDER must identify this run attempt}"
case "${1:?usage: run-scoped-buildx.sh prepare|cleanup}" in
  prepare)
    docker buildx rm "$builder" >/dev/null 2>&1 || true
    docker buildx create --name "$builder" --driver docker-container >/dev/null
    docker buildx inspect "$builder" --bootstrap >/dev/null
    ;;
  cleanup) docker buildx rm "$builder" >/dev/null 2>&1 || true ;;
  *) echo "Unknown run-scoped Buildx operation: $1" >&2; exit 2 ;;
esac
