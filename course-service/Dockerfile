# ── Étape 1 : compilation ─────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
# Dépendances d'abord : couche mise en cache tant que le pom ne change pas
COPY pom.xml .
RUN mvn -q dependency:go-offline -B
COPY src ./src
RUN mvn -q clean package -DskipTests

# ── Étape 2 : exécution (image légère, utilisateur non root) ──
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
# Réglages mémoire pour les instances Render gratuites (512 Mo)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
