FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home app && mkdir /uploads && chown app /uploads
USER app
WORKDIR /home/app
COPY --from=build /app/target/*.jar app.jar
ENV UPLOAD_DIR=/uploads
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
