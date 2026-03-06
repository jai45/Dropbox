# ── Stage 1: Build ──────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first and download dependencies (layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the fat JAR (skip tests for faster image builds)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Own the file as the non-root user
RUN chown appuser:appgroup app.jar

USER appuser

# Render injects PORT env var; Spring Boot reads SERVER_PORT
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
