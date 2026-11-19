FROM maven:3.9-eclipse-temurin-11 AS compilacion
WORKDIR /proyecto
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeArtifactIds=pdfbox,fontbox -DoutputDirectory=/portal-libs

FROM flowable/flowable-ui:6.8.0
# El entrypoint de esta imagen incluye /app/WEB-INF/lib/* en el classpath.
COPY --from=compilacion /proyecto/target/ProyectoCursos-1.0-SNAPSHOT.jar /app/WEB-INF/lib/proyecto-cursos.jar
COPY --from=compilacion /portal-libs/ /app/WEB-INF/lib/
