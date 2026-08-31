ARG SOURCE_ROOT=.

FROM node:24-alpine AS frontend-build
ARG SOURCE_ROOT
WORKDIR /frontend
COPY ${SOURCE_ROOT}/frontend/package.json ./package.json
RUN npm install
COPY ${SOURCE_ROOT}/frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS application-build
ARG SOURCE_ROOT
WORKDIR /workspace
COPY ${SOURCE_ROOT}/backend/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY ${SOURCE_ROOT}/backend/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package \
    && cp target/reference-app-0.1.0-SNAPSHOT.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=application-build /tmp/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
