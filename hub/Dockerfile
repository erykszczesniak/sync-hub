# Build stage: the Gradle wrapper builds only the hub module; tests run in CI, not in the image build.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY config ./config
COPY hub ./hub
COPY system-a-sim/build.gradle.kts ./system-a-sim/build.gradle.kts
RUN ./gradlew :hub:bootJar -x test --no-daemon -q

# Runtime stage: JRE only, non-root, health check on the actuator.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S synchub && adduser -S synchub -G synchub && apk add --no-cache curl
USER synchub
WORKDIR /app
COPY --from=build /workspace/hub/build/libs/hub.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD curl -fs http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
