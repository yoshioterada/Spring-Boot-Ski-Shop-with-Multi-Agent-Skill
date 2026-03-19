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
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -pl common-lib,authentication-service -am
COPY common-lib/src common-lib/src
COPY authentication-service/src authentication-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B -pl common-lib,authentication-service -am

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup -d /app -s /sbin/nologin appuser

COPY --from=build --chown=appuser:appgroup /workspace/authentication-service/target/*.jar app.jar
USER appuser
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
