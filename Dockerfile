FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests

RUN mvn clean package -DskipTests

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]