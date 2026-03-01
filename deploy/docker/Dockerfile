FROM openjdk:8-jdk-alpine

LABEL maintainer="OSG Team"
LABEL description="OpenServerlessGateway"

WORKDIR /app

COPY osg-server/target/osg-server-1.0.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
ENV TZ=Asia/Shanghai

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
