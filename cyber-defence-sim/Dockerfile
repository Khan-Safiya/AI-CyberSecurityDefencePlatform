FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY pom.xml .
COPY shared shared
COPY services services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl services/${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG SERVICE
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/services/${SERVICE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
