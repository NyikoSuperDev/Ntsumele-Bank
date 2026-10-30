# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e
# Build and test the Angular client.
FROM node:24-alpine@sha256:8a32c8cf649a49915a66e81e7a609223c708d0adabf288ee3f4077d765542f85 AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm test && npm run build

# Build the API with unit/architecture checks; PostgreSQL integration runs in CI.
FROM maven:3.9.11-eclipse-temurin-21-alpine@sha256:922927df2c662cdd47ddb116443d6bec4696cfae3de1a0ddac8fcc7b87ce61ae AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist/ntsumele-bank/browser/ /build/frontend/dist/ntsumele-bank/browser/
# Resolve only what the package lifecycle needs, retaining downloads across builds.
# Batch logging keeps first-build downloads visible; the cache never enters the runtime image.
RUN --mount=type=cache,id=ntsumele-maven,target=/root/.m2,sharing=locked mvn -B -ntp package

FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088 AS runtime
RUN addgroup -S -g 10001 bank && adduser -S -D -H -u 10001 -G bank bank
WORKDIR /app
COPY --from=backend --chown=bank:bank /build/backend/target/*.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
