# Build context must be the repo root (see docker-compose.yml):
#   docker build -t producer -f producer/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# All module poms are needed so Maven can resolve the multi-module reactor
COPY pom.xml .
COPY producer/pom.xml producer/
COPY consumer-v3/pom.xml consumer-v3/
COPY test-client/pom.xml test-client/
COPY producer/src producer/src
RUN mvn -q clean package -DskipTests -pl producer -am

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/producer/target/producer-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
