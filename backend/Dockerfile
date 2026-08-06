FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -pl backend dependency:go-offline
COPY backend/src backend/src
RUN mvn -B -pl backend clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 smartqueue
WORKDIR /app
COPY --from=build /workspace/backend/target/smartqueue-backend-*.jar app.jar
USER smartqueue
EXPOSE 8080 9091
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
