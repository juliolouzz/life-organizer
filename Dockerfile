# Multi-stage build for the Spring Boot backend.
# Stage 1: build the fat jar with Maven against a JDK 21 image.
# Stage 2: copy only the jar into a minimal JDK 21 runtime image (no Maven, no cache).

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache Maven dependencies in a separate layer so iterative rebuilds are fast.
COPY pom.xml ./
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package \
    && cp target/life-organizer-*.jar /workspace/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as non-root user.
RUN groupadd --system spring && useradd --system --gid spring spring \
    && mkdir -p /app && chown -R spring:spring /app
USER spring

COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar

EXPOSE 8080

# Healthcheck uses the actuator endpoint that we expose unauthenticated.
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
