# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy pom files first for better caching
COPY pom.xml .
COPY Frameworks ./Frameworks
COPY Services ./Services

# Build with dependencies
RUN mvn dependency:go-offline -B || true
RUN mvn clean package -pl Services/seat-service -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install necessary tools
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built jar
COPY --from=builder /build/Services/seat-service/target/seat-service-1.0-SNAPSHOT.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JAVA_OPTS_OVERRIDE -jar app.jar"]
