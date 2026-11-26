FROM eclipse-temurin:24-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew :finance-service:bootJar --no-daemon
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/finance-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]
