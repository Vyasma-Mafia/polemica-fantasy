# Stage 1: Build
FROM gradle:jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew /app/
COPY gradle /app/gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true
COPY src /app/src
RUN ./gradlew --no-daemon build -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY --from=build /app/build/libs/app.jar /app/app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
