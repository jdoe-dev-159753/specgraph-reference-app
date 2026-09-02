# syntax=docker/dockerfile:1
ARG SOURCE_ROOT=.

# Java bytecode and browser assets are platform-neutral, so compile them once on
# the builder architecture instead of emulating the target runtime architecture.
FROM --platform=$BUILDPLATFORM node:24-alpine AS frontend-build
ARG SOURCE_ROOT
WORKDIR /frontend
COPY ${SOURCE_ROOT}/frontend/package.json ./package.json
RUN npm install
COPY ${SOURCE_ROOT}/frontend/ ./
RUN npm run build

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS application-build
ARG SOURCE_ROOT
WORKDIR /workspace
COPY ${SOURCE_ROOT}/backend/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY ${SOURCE_ROOT}/backend/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package \
    && cp target/reference-app-0.1.0-SNAPSHOT.jar /tmp/app.jar

# Only the runtime base varies by TARGETPLATFORM. Avoid target-architecture RUN
# instructions so linux/amd64 + linux/arm64 manifests need no QEMU emulation.
# Use a glibc-based JRE because the activated R4 local transformer path loads
# ONNX Runtime native libraries that are not compatible with Alpine/musl.
FROM eclipse-temurin:21-jre-noble
ENV HOME=/tmp
WORKDIR /app
COPY --chown=10001:10001 --from=application-build /tmp/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
