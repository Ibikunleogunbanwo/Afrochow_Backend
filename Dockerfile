FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /opt/afrochow

RUN useradd --system --home-dir /opt/afrochow --shell /usr/sbin/nologin afrochow

COPY --from=build /workspace/target/*.jar /opt/afrochow/afrochow.jar

RUN mkdir -p /opt/afrochow/uploads && chown -R afrochow:afrochow /opt/afrochow

USER afrochow
EXPOSE 8081

ENV JAVA_OPTS="-Xms256m -Xmx768m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /opt/afrochow/afrochow.jar"]
