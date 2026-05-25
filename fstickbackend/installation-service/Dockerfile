# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install maven
RUN apk add --no-cache maven

WORKDIR /build

# Copy maven build files
COPY pom.xml .

# Copy source code
COPY src/ src/

# Build the project
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]