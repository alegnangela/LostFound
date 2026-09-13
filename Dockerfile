# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jre-noble

LABEL org.opencontainers.image.title="lost-found-service"

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
# The jar built by CI (mvn package); .dockerignore keeps only target/*.jar in the build context
COPY target/lost-found-service-*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
