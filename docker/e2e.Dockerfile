# syntax=docker/dockerfile:1.7
FROM mcr.microsoft.com/playwright:v1.55.0-noble

ARG E2E_INPUTS_SHA256=unknown
LABEL io.specgraph.e2e.inputs-sha256="${E2E_INPUTS_SHA256}"

WORKDIR /opt/specgraph-e2e
COPY e2e/package.json ./
RUN --mount=type=cache,id=specgraph-e2e-npm,target=/root/.npm \
    npm install --ignore-scripts --no-package-lock --no-audit --no-fund

ENV PATH="/opt/specgraph-e2e/node_modules/.bin:${PATH}"
