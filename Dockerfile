# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build

WORKDIR /src

# Copy the Maven wrapper first so the image builds with the exact same Maven
# version (3.9.16) as CI and local dev.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Warm the dependency cache in its own layer. This step only re-runs when
# pom.xml changes, so day-to-day code edits reuse the cached dependencies.
RUN ./mvnw -B dependency:go-offline

# Copy sources and build the executable jar. Tests run in CI, so they are
# skipped here to keep image builds fast (drop -DskipTests to gate on them).
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run as an unprivileged user and give it a writable logs directory.
RUN useradd -u 1001 -m appuser \
    && mkdir -p /app/logs \
    && chown -R appuser:appuser /app

# Copy only the executable Spring Boot jar. `package` also emits
# *.jar.original (the pre-repackage jar), which this glob does not match.
COPY --from=build --chown=appuser:appuser /src/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
