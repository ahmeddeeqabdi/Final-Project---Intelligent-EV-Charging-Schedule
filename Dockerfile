FROM docker.io/library/maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend backend
COPY frontend frontend

RUN mvn --batch-mode -f backend/pom.xml -DskipTests package

FROM docker.io/library/eclipse-temurin:25-jre

RUN useradd --system --uid 10001 --create-home evscheduler
WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/backend/target/sdu-0.0.1-SNAPSHOT.jar /app/ev-charging-scheduler.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/ev-charging-scheduler.jar"]
