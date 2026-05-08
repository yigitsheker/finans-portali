# Multi-stage build for Spring Boot application
FROM eclipse-temurin:21-jdk-alpine as builder

WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src src
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl and wget for healthcheck and downloading agent
RUN apk add --no-cache curl wget

# Create non-root user, logs directory, and otel directory
RUN addgroup -S appuser && adduser -S appuser -G appuser && \
    mkdir -p /app/logs /otel && \
    chown -R appuser:appuser /app /otel

# Download OpenTelemetry Java Agent
RUN wget -O /otel/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.0.0/opentelemetry-javaagent.jar && \
    chmod 644 /otel/opentelemetry-javaagent.jar && \
    chown appuser:appuser /otel/opentelemetry-javaagent.jar

# Copy jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# Run with OpenTelemetry Java Agent
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]