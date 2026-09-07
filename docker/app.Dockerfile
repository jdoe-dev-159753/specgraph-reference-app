# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
# Multi-stage production image compiles frontend/backend once and records exact source/recipe provenance.
ARG SOURCE_ROOT=.
ARG SOURCE_REVISION=unknown
ARG BUILD_RECIPE_SHA256=unknown

# Java bytecode and browser assets are platform-neutral, so compile them once on
# the builder architecture instead of emulating the target runtime architecture.
FROM --platform=$BUILDPLATFORM node:24-alpine@sha256:e67514e5d0f6c46656005e1b693b2ec9d52e80b641307de684d4a015ba7a4eaf AS frontend-build
ARG SOURCE_ROOT
ARG DELIVERY_RING=R4
ENV VITE_DELIVERY_RING=${DELIVERY_RING}
WORKDIR /frontend
COPY ${SOURCE_ROOT}/frontend/package.json ./package.json
RUN --mount=type=cache,id=specgraph-frontend-npm,target=/root/.npm \
    npm install
COPY ${SOURCE_ROOT}/frontend/ ./
RUN npm run build

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8 AS application-build
ARG SOURCE_ROOT
WORKDIR /workspace
COPY ${SOURCE_ROOT}/backend/pom.xml ./pom.xml
RUN --mount=type=cache,id=specgraph-maven,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline
COPY ${SOURCE_ROOT}/backend/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN --mount=type=cache,id=specgraph-maven,target=/root/.m2 \
    mvn -B -DskipTests package \
    && cp target/reference-app-0.1.0-SNAPSHOT.jar /tmp/app.jar

# Only the runtime base varies by TARGETPLATFORM. Avoid target-architecture RUN
# instructions so linux/amd64 + linux/arm64 manifests need no QEMU emulation.
# Use a glibc-based JRE because the activated R4 local transformer path loads
# ONNX Runtime native libraries that are not compatible with Alpine/musl.
FROM eclipse-temurin:21-jre-noble@sha256:96975602e131485862eb8cd32927face8a06d7591a5e865944b634a701d9df72
ARG SOURCE_ROOT
ARG SOURCE_REVISION
ARG BUILD_RECIPE_SHA256
LABEL org.opencontainers.image.revision="${SOURCE_REVISION}" \
      io.specgraph.source-root="${SOURCE_ROOT}" \
      io.specgraph.build-recipe-sha256="${BUILD_RECIPE_SHA256}"
ENV HOME=/tmp
WORKDIR /app
COPY --chown=10001:10001 --from=application-build /tmp/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
