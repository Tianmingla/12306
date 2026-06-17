# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy pom files first for better caching
COPY pom.xml .
COPY Frameworks/pom.xml ./Frameworks/pom.xml
COPY Frameworks/cache/pom.xml ./Frameworks/cache/pom.xml
COPY Frameworks/common/pom.xml ./Frameworks/common/pom.xml
COPY Frameworks/database/pom.xml ./Frameworks/database/pom.xml
COPY Frameworks/Idempotent/pom.xml ./Frameworks/Idempotent/pom.xml
COPY Frameworks/log/pom.xml ./Frameworks/log/pom.xml
COPY Frameworks/mq/pom.xml ./Frameworks/mq/pom.xml
COPY Services/pom.xml ./Services/pom.xml
COPY Services/order-service/pom.xml ./Services/order-service/pom.xml
COPY Services/ticket-service/pom.xml ./Services/ticket-service/pom.xml
COPY Services/seat-service/pom.xml ./Services/seat-service/pom.xml
COPY Services/gateway-service/pom.xml ./Services/gateway-service/pom.xml
COPY Services/user-service/pom.xml ./Services/user-service/pom.xml
COPY Services/admin-service/pom.xml ./Services/admin-service/pom.xml

#Download dependencies
RUN mvn dependency:go-offline -B
# Copy files 
COPY Services ./Services
COPY Frameworks ./Frameworks
# Build with dependencies
RUN mvn clean package -pl Services/gateway-service -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install necessary tools
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built jar
COPY --from=builder /build/Services/gateway-service/target/gateway-service-1.0-SNAPSHOT.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JAVA_OPTS_OVERRIDE -jar app.jar"]
