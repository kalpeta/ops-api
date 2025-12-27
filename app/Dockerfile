# =========================
# Stage 1: Build (kitchen)
# =========================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy only build descriptors first (better Docker layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached if pom.xml doesn't change)
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

# Now copy the actual source
COPY src/ src/

# Build the runnable jar
RUN ./mvnw -q -DskipTests package

# =========================
# Stage 2: Runtime (lunchbox)
# =========================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add curl only so HEALTHCHECK can call /actuator/health
RUN apk add --no-cache curl

# Copy the jar from build stage
COPY --from=build /workspace/target/*.jar /app/app.jar

# App listens on 8080 by default
EXPOSE 8080

# Container healthcheck: consider container healthy only if actuator health is UP
HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
