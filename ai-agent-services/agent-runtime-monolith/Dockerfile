# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .. /workspace
RUN ./mvnw -pl ai-agent-services/agent-runtime-monolith -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/ai-agent-services/agent-runtime-monolith/target/agent-runtime-monolith-*.jar /app/app.jar
EXPOSE 8100
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8100/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
