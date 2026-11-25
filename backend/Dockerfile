FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system renewiq && useradd --system --gid renewiq renewiq
WORKDIR /app
COPY --from=build --chown=renewiq:renewiq /app/target/renewiq-backend-1.0.0.jar app.jar
USER renewiq
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
