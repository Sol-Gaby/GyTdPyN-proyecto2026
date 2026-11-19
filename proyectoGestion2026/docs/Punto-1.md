# Punto 1 — Organización de cursos en Flowable

El BPMN original de la cátedra está en `docs/Proyecto_Organizacion_cursos_168448699638.bpmn`. El archivo ejecutable es `src/main/resources/processes/organizacion-cursos.bpmn20.xml`, con clave `Process_Curso`. Conserva el diagrama, los identificadores de las tareas del profesor y las ramas de la Figura 1, con los ajustes explicados abajo.

## Arrancar y desplegar en Windows

1. Abrí Docker Desktop y esperá a que indique que el motor está en ejecución. En CMD, desde la **raíz del repositorio**, recuperá la rama. Si ya tenés `version1` local, usá `git switch version1`.

   ```bat
   git fetch origin
   git switch --track origin/version1
   cd flowable-cursos
   ```

2. Creá `.env` desde la plantilla. Si ya tenés un `.env` local para esta rama, abrilo directamente.

   ```bat
   copy .env.example .env
   notepad .env
   ```

   Completá `POSTGRES_PASSWORD` y `CURSOS_PERSONAL_PASSWORD` con claves locales de prueba. Guardá respetando el nombre `.env`. La segunda clave es la contraseña inicial de las cinco cuentas de demostración. Volver a ejecutar la instalación conserva las contraseñas de las cuentas existentes. El Compose de esta rama tiene nombre propio y crea un volumen nuevo. Si otro contenedor ya está usando el puerto 8082 o 5433, identificálo con `docker ps` y detené ese entorno anterior antes de arrancar este.

3. Desde `flowable-cursos`, ejecutá:

   ```bat
   docker compose config -q
   docker compose up -d --build
   docker compose ps -a
   docker compose logs --tail=100 flowable
   ```

   `config -q` valida la configuración. `up -d --build` construye el JAR Java y lo incluye en la imagen de Flowable UI 6.8.0. Esperá que `database` figure `healthy` y `flowable` figure `Up`. Si todavía está inicializando, esperá el mensaje de arranque de la UI en los logs. Abrí [Flowable UI](http://localhost:8082/flowable-ui/) con `admin` / `test` en una instalación nueva.

4. Instalá el BPMN, la decisión y los usuarios. Este comando se ejecuta en el contenedor y usa la misma base que la UI. Ejecutalo después del primer arranque y cada vez que reconstruyas la imagen con un modelo nuevo.

   ```bat
   docker compose exec -T flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.InstalarCursos
   curl.exe -i -u admin:test "http://localhost:8082/flowable-ui/process-api/repository/process-definitions?key=Process_Curso"
   ```

   Esperá `Process_Curso disponible` y un HTTP 200 con `Process_Curso` en `data`. La UI y REST atienden en el puerto 8082. PostgreSQL está disponible para un cliente local en `localhost:5433`, y entre contenedores en `database:5432`.

   Podés detener los servicios con `docker compose stop` y levantarlos otra vez con `docker compose up -d`. El volumen de PostgreSQL conserva las tareas y el historial.

## Probar sin Docker en IntelliJ

1. Abrí `proyectoGestion2026/pom.xml` con **File → Open → Open as Project**. Usá un JDK 11 o superior. El POM compila para Java 11. Hacé **View → Tool Windows → Maven → Reload All Maven Projects**.
2. Expandí **ProyectoCursos → Lifecycle → test** y hacé doble clic. También podés entrar a `proyectoGestion2026` y ejecutar `mvn test` si tenés Maven en el PATH.
3. Buscá `BUILD SUCCESS`, con cero errores y cero fallos. Si preferís ejecutar solo el flujo de cursos desde el editor, abrí `src/test/java/proyecto/cursos/OrganizacionCursosTest.java` y usá el triángulo al lado de la clase.
4. Las pruebas crean su propia base H2, inicializan el motor BPMN/DMN y adelantan su reloj. La espera de cinco días se comprueba sin esperar cinco días reales. `ejemploMinimoDespliegaYCompletaUnaTarea` cubre el archivo elemental del ejercicio, y las demás pruebas recorren el modelo de cursos.

## Probar manualmente con Postman y Flowable UI

La [guía de puntos 2 y 3](Puntos-2-y-3.md#postman) actualiza el recorrido manual de este punto. El usuario técnico `admin` puede enviar comunicaciones y consultar instancias. Las tareas se reclaman y completan con la sesión del responsable.
 
## Cambios de diseño y código

- **`organizacion-cursos.bpmn20.xml`.** A partir del material de la cátedra se agregó el conteo previo, se vinculó la tarea de decisión con el DMN, se completaron mensajes y condiciones y se conservaron las uniones AND/OR. La nómina ahora es un evento de captura para identificar su suscripción por REST. Las asignaciones se expresan en el modelo, con un listener para la lista definitiva.
- **`viabilidad-curso.dmn`.** La entrada `cantidadOtrosCursos` es el conteo de instancias actuales de la misma clave, incluso si pertenecen a versiones anteriores. La salida `viable` es verdadera para cantidades menores de cinco. La consulta del motor excluye el ID que está en evaluación. Con PostgreSQL, un bloqueo de transacción serializa esta evaluación y evita admitir dos solicitudes a la vez para el último cupo.
- **`OperacionesCurso.java`.** Adapta el patrón de `ProcesarOrden` a las tareas de servicio del modelo. Reinicia el contador en cada propuesta, incrementa y registra cada recordatorio, valida datos recibidos, simula la reserva del aula y deja variables del estado de liberación. Los avisos son visibles en logs y en `ultimoAvisoEmpresa`.
- **`TareasCurso.java`.** Adapta `AsignarTarea` al grupo Administración. En la creación de la lista definitiva cuenta pendientes asignadas, con empate por ID. Al completar tareas valida el reclamo y la pertenencia a Administración, los datos de propuesta y recursos, la pertenencia del docente seleccionado y la referencia al informe. El listener de la pausa valida que las liberaciones hayan terminado antes de permitir el cierre.
- **Compensación.** La cancelación por mensaje interrumpe la preparación. Los tres lanzamientos tienen `activityRef` para las gestiones de aula física, virtual y equipamiento. Después queda la tarea **Verificar compensaciones**. Al completarla, la XOR conduce a la salida de cancelación. La ruta normal confirma el curso y usa la compensación de `Preparar` al finalizar el expediente. [Prueba de compensaciones](Predefinitiva.md#5-verificar-compensaciones-y-la-salida-por-xor).
- **`InstalarCursos.java`.** Crea los dos grupos y cinco usuarios si faltan, y despliega DMN y BPMN usando el mismo PostgreSQL. El filtrado de duplicados evita nuevas versiones por repetir una instalación idéntica.
- **`pom.xml` y `Miprimerproceso.bpmn20.xml`.** Se alinearon BPMN y DMN en Flowable 6.8.0, se compiló para Java 11 y se configuró JUnit. El archivo de ejemplo, originalmente vacío, ahora contiene inicio, tarea **Registrar orden** y fin.
- **`flowable-cursos/`.** Compose usa la edición Open Source y una sola publicación del puerto 8082. El Dockerfile añade al motor el JAR con las clases del BPMN. `.env.example` documenta las variables del entorno. `.env` queda local y Git lo ignora.

En el punto 1 los avisos, las reservas y la habilitación de acceso son simulaciones observables. La cuenta real de la empresa, el sitio web móvil, el almacenamiento de informes y la base institucional se integran en los incisos 3 y 4. En este tramo, el informe se representa por una referencia y el usuario docente se selecciona como variable de tarea.
