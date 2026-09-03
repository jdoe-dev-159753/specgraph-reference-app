# syntax=docker/dockerfile:1.7
FROM mcr.microsoft.com/playwright:v1.55.0-noble

ARG E2E_LOCK_SHA256=unknown
LABEL io.specgraph.e2e.lock-sha256="${E2E_LOCK_SHA256}"

WORKDIR /opt/specgraph-e2e
COPY e2e/package.json e2e/package-lock.json ./
RUN --mount=type=cache,id=specgraph-e2e-npm,target=/root/.npm \
    npm ci --ignore-scripts

ENV PATH="/opt/specgraph-e2e/node_modules/.bin:${PATH}"
