#!/usr/bin/env bash
# Creates and reclaims the Buildx builder owned by one workflow run attempt.
set -euo pipefail

builder="${BUILDX_BUILDER:?BUILDX_BUILDER must identify this run attempt}"
BUILDKIT_IMAGE="moby/buildkit:buildx-stable-1@sha256:28a898719c18a33f4e8000685287fa36fd0dd9560c6440227d3a732d79bb41d8"
case "${1:?usage: run-scoped-buildx.sh prepare|cleanup}" in
  prepare)
    docker buildx rm "$builder" >/dev/null 2>&1 || true
    docker buildx create --name "$builder" --driver docker-container \
      --driver-opt "image=$BUILDKIT_IMAGE" >/dev/null
    docker buildx inspect "$builder" --bootstrap >/dev/null
    ;;
  cleanup) docker buildx rm "$builder" >/dev/null 2>&1 || true ;;
  *) echo "Unknown run-scoped Buildx operation: $1" >&2; exit 2 ;;
esac
