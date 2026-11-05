FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B verify
FROM tomcat:10.1-jdk21-temurin
RUN rm -rf /usr/local/tomcat/webapps/* && chown -R 1000:1000 /usr/local/tomcat
COPY --from=build /src/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
USER 1000
EXPOSE 8080
