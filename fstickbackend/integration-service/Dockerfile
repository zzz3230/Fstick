# Stage 1: Build stage
FROM gradle:8-jdk21-alpine AS builder

WORKDIR /build

# Copy gradle build files
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src/ src/

# Build the project
RUN gradle bootJar --no-daemon

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /build/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

