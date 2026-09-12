FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY config ./config
COPY system-a-sim ./system-a-sim
COPY hub/build.gradle.kts ./hub/build.gradle.kts
RUN ./gradlew :system-a-sim:bootJar -x test --no-daemon -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S systema && adduser -S systema -G systema && apk add --no-cache curl
USER systema
WORKDIR /app
COPY --from=build /workspace/system-a-sim/build/libs/system-a-sim.jar app.jar
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD curl -fs http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
