# syntax=docker/dockerfile:1.7
ARG APP_R4_IMAGE

FROM ${APP_R4_IMAGE} AS exact-head

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS fixture-build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN --mount=type=cache,id=specgraph-maven,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline
COPY backend/src ./src
COPY e2e/fixtures/backend/DegradationTestConfiguration.java \
    ./src/main/java/dev/specgraph/reference/analysis/DegradationTestConfiguration.java
RUN --mount=type=cache,id=specgraph-maven,target=/root/.m2 \
    mvn -B -DskipTests package
COPY --from=exact-head /app/app.jar /tmp/app.jar
RUN mkdir -p /tmp/fixture/BOOT-INF/classes \
    && cd target/classes \
    && find dev/specgraph/reference/analysis -name 'DegradationTestConfiguration*.class' \
        -exec cp --parents {} /tmp/fixture/BOOT-INF/classes \; \
    && jar uf /tmp/app.jar -C /tmp/fixture BOOT-INF/classes

FROM exact-head
USER root
COPY --chown=10001:10001 --from=fixture-build /tmp/app.jar /app/app.jar
USER 10001:10001
LABEL io.specgraph.test-fixture="r4-degradation"
