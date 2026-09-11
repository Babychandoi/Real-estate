# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Tận dụng cache layer dependency của Docker
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B -DskipTests package

# Runtime container với user non-root bảo mật
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
