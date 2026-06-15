FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY ../pom.xml ../pom.xml
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests -pl . -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/producer-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
