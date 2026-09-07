# Construcción
FROM maven:3.9-eclipse-temurin-21 AS construir
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Ejecución
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construir /app/target/*.jar app.jar

# Railway asigna el puerto por la variable PORT
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Bogota"
EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]
