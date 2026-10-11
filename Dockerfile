# Giai đoạn 1: build WAR
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# Giai đoạn 2: chạy trên Tomcat 10.1
FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
# Render cấp cổng qua biến PORT (mặc định 10000) -> cho Tomcat nghe đúng cổng đó
CMD ["sh", "-c", "sed -i \"s/Connector port=\\\"8080\\\"/Connector port=\\\"${PORT:-10000}\\\"/\" /usr/local/tomcat/conf/server.xml && exec catalina.sh run"]
