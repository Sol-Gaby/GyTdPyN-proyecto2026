FROM maven:3.9-eclipse-temurin-11 AS compilacion
WORKDIR /proyecto
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM flowable/flowable-ui:6.8.0
# El entrypoint de esta imagen incluye /app/WEB-INF/lib/* en el classpath.
COPY --from=compilacion /proyecto/target/ProyectoCursos-1.0-SNAPSHOT.jar /app/WEB-INF/lib/proyecto-cursos.jar
