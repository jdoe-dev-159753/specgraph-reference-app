# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
# Pins browser dependencies separately from product code so E2E runs remain repeatable and cacheable.
FROM mcr.microsoft.com/playwright:v1.55.0-noble@sha256:b27e719ecbfef153e13fd24e8341736733bf2658b229677eb21ff57ff5d7fb29

ARG E2E_INPUTS_SHA256=unknown
LABEL io.specgraph.e2e.inputs-sha256="${E2E_INPUTS_SHA256}"

WORKDIR /opt/specgraph-e2e
COPY e2e/package.json ./
RUN --mount=type=cache,id=specgraph-e2e-npm,target=/root/.npm \
    npm install --ignore-scripts --no-package-lock --no-audit --no-fund

ENV PATH="/opt/specgraph-e2e/node_modules/.bin:${PATH}"
