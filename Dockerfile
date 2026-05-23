# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Life Organizer backend - multi-stage container (Slice 11 hardening pass).
#
# Stage 1 builds the fat jar with Maven on a JDK 21 image, skipping tests
# (CI runs them before the image is built).
# Stage 2 copies just the jar into a slim JRE alpine runtime, drops to a
# dedicated non-root user, and uses tini as PID 1 for clean signal handling.
# ---------------------------------------------------------------------------

# ---- build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

RUN apk add --no-cache maven

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package \
    && cp target/life-organizer-*.jar /workspace/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# tini handles PID 1 + signal forwarding; wget is used by the HEALTHCHECK.
RUN apk add --no-cache tini wget

# Dedicated non-root user; uid > 10000 to avoid clashing with host users.
RUN addgroup -S -g 10001 lifeorg \
    && adduser -S -G lifeorg -u 10001 -h /app lifeorg \
    && mkdir -p /app/.tmp \
    && chown -R lifeorg:lifeorg /app

WORKDIR /app
USER lifeorg

COPY --from=build --chown=lifeorg:lifeorg /workspace/app.jar /app/app.jar

# Respect cgroup memory limits and seed urandom for faster JVM boot.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "/app/app.jar"]
