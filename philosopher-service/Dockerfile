# Image du philosopher-service : construction Maven puis image d'exécution légère (JRE seul)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Les dépendances d'abord : cette couche reste en cache tant que le pom.xml ne change pas
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
# Utilisateur non-root
RUN useradd --system --uid 1001 --no-create-home app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 1001
EXPOSE 8080
# La JVM s'adapte à la mémoire du conteneur (limites Docker / Kubernetes)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
