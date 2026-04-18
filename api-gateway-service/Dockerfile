FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
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
    mvn dependency:go-offline -B -pl api-gateway-service
COPY api-gateway-service/src api-gateway-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B -pl api-gateway-service

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup -d /app -s /sbin/nologin appuser

COPY --from=build --chown=appuser:appgroup /workspace/api-gateway-service/target/*.jar app.jar
USER appuser
EXPOSE 8090

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
