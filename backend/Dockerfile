# Lightweight Runtime Image
# Maven build is done locally; Docker only runs the compiled JAR
FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
