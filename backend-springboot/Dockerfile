# syntax=docker/dockerfile:1
# Build context: the repository root (the jar packages ../bpmn-models). See docker-compose.yml.

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend-springboot/pom.xml backend-springboot/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -f backend-springboot/pom.xml dependency:go-offline || true
COPY bpmn-models bpmn-models
COPY backend-springboot/src backend-springboot/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -f backend-springboot/pom.xml package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --home /app app && mkdir -p /app/data && chown app:app /app/data
COPY --from=build /src/backend-springboot/target/condonation-backend.jar app.jar
USER app
ENV DB_URL=jdbc:h2:file:/app/data/condonation
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
