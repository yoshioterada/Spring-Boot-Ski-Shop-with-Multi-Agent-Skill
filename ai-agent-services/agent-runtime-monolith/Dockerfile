# syntax=docker/dockerfile:1.7
# Multi-stage Maven build (pattern matches other services in this repo)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy parent POM and all module POMs (for offline dependency resolution)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/pom.xml
COPY authentication-service/pom.xml authentication-service/pom.xml
COPY user-management-service/pom.xml user-management-service/pom.xml
COPY inventory-management-service/pom.xml inventory-management-service/pom.xml
COPY sales-management-service/pom.xml sales-management-service/pom.xml
COPY payment-cart-service/pom.xml payment-cart-service/pom.xml
COPY point-service/pom.xml point-service/pom.xml
COPY coupon-service/pom.xml coupon-service/pom.xml
COPY ai-support-service/pom.xml ai-support-service/pom.xml
COPY api-gateway-service/pom.xml api-gateway-service/pom.xml
COPY mailsend-service/pom.xml mailsend-service/pom.xml
COPY ai-agent-services/pom.xml ai-agent-services/pom.xml
COPY ai-agent-services/agent-common/pom.xml ai-agent-services/agent-common/pom.xml
COPY ai-agent-services/weather-agent/pom.xml ai-agent-services/weather-agent/pom.xml
COPY ai-agent-services/customer-intent-agent/pom.xml ai-agent-services/customer-intent-agent/pom.xml
COPY ai-agent-services/equipment-matching-agent/pom.xml ai-agent-services/equipment-matching-agent/pom.xml
COPY ai-agent-services/inventory-monitoring-agent/pom.xml ai-agent-services/inventory-monitoring-agent/pom.xml
COPY ai-agent-services/dynamic-pricing-agent/pom.xml ai-agent-services/dynamic-pricing-agent/pom.xml
COPY ai-agent-services/coupon-optimization-agent/pom.xml ai-agent-services/coupon-optimization-agent/pom.xml
COPY ai-agent-services/orchestrator-agent/pom.xml ai-agent-services/orchestrator-agent/pom.xml
COPY ai-agent-services/agent-runtime-monolith/pom.xml ai-agent-services/agent-runtime-monolith/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -pl ai-agent-services/agent-runtime-monolith -am

# Copy sources only for the modules we need
COPY common-lib/src common-lib/src
COPY ai-agent-services/agent-common/src ai-agent-services/agent-common/src
COPY ai-agent-services/weather-agent/src ai-agent-services/weather-agent/src
COPY ai-agent-services/customer-intent-agent/src ai-agent-services/customer-intent-agent/src
COPY ai-agent-services/equipment-matching-agent/src ai-agent-services/equipment-matching-agent/src
COPY ai-agent-services/inventory-monitoring-agent/src ai-agent-services/inventory-monitoring-agent/src
COPY ai-agent-services/dynamic-pricing-agent/src ai-agent-services/dynamic-pricing-agent/src
COPY ai-agent-services/coupon-optimization-agent/src ai-agent-services/coupon-optimization-agent/src
COPY ai-agent-services/orchestrator-agent/src ai-agent-services/orchestrator-agent/src
COPY ai-agent-services/agent-runtime-monolith/src ai-agent-services/agent-runtime-monolith/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B -pl ai-agent-services/agent-runtime-monolith -am

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/ai-agent-services/agent-runtime-monolith/target/agent-runtime-monolith-*.jar /app/app.jar
EXPOSE 8100
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8100/actuator/health || exit 1
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
