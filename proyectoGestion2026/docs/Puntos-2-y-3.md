# Puntos 2 y 3 — Modeler, personal y datos institucionales

## Preparar el entorno en Windows

1. Abrí Docker Desktop. En **CMD**, desde la raíz del repositorio, recuperá la rama y entrá a la carpeta de Docker.

   ```bat
   git switch version1
   git pull --ff-only origin version1
   cd flowable-cursos
   dir /a /b .env*
   ```

   Si es la primera vez que usás la rama y Git dice que no existe localmente, ejecutá `git fetch origin` y luego `git switch --track origin/version1`. Si `git pull --ff-only` dice que hay cambios locales o que las ramas divergieron, revisá `git status -sb` antes de sincronizar. Conservá tu `.env` local. Si falta, ejecutá `copy .env.example .env`, abrilo con `notepad .env` y completá las dos contraseñas. Conservá `POSTGRES_DB=flowable` y `POSTGRES_USER=flowable` para usar literalmente los comandos de esta guía. Si el volumen se creó con otros valores, mantené esos valores y adaptá los comandos `psql`.

2. Validá y levantá la instalación.

   ```bat
   docker compose config -q
   docker compose up -d --build
   docker compose ps -a
   docker compose logs preparar-institucion
   docker compose logs --tail=70 flowable
   ```

   Esperá `database` en **healthy**, `preparar-institucion` en **Exited (0)** y `flowable` en **Up**. `preparar-institucion` termina porque su trabajo es ejecutar los scripts SQL. Compose usa el mismo nombre de proyecto y volumen que el punto 1. En un volumen existente, los scripts agregan la base institucional y las tablas faltantes. Las credenciales del contenedor PostgreSQL se aplican al inicializar el volumen.

3. Una vez que la interfaz responde, desplegá la revisión y sus recursos.

   ```bat
   docker compose exec -T flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.InstalarCursos
   curl.exe -i -u admin:test "http://localhost:8082/flowable-ui/process-api/repository/process-definitions?key=Process_Curso&latest=true"
   ```

   El instalador publica cuatro formularios, el DMN y el BPMN. También crea el personal si falta. El primer comando muestra `Process_Curso disponible` y el segundo devuelve `200 OK` con la definición. Si ya había una definición anterior, empezá una **solicitud nueva** para probar esta revisión. Las instancias antiguas conservan su definición y no se migran automáticamente a la base institucional. Si una cuenta ya existía, conserva su contraseña original.

## Ver el diagrama en Flowable Modeler

1. Abrí [Flowable UI](http://localhost:8082/flowable-ui/) con `admin` / `test` para una instalación inicial.
2. En el panel general seleccioná **Modeler**.
3. En **Business Process Models**, pulsá **Import process model**.
4. En **Browse** elegí o arrastrá este archivo desde el repo local:

   ```text
   proyectoGestion2026\src\main\resources\processes\organizacion-cursos.bpmn20.xml
   ```

5. Abrí la tarjeta **Organizar curso de capacitación**. En el editor vas a ver los carriles, la negociación y el subproceso **Preparar curso**. Usá el botón para ajustar el zoom si el diagrama queda fuera de la vista. El archivo de prueba `Miprimerproceso.bpmn20.xml` muestra un proceso diferente y el archivo bajo `docs` es el original de la cátedra.

**Importar** guarda una copia editable para Modeler. **InstalarCursos** despliega el ejecutable, con sus formularios, DMN y clases Java. Para correr la implementación de esta rama usá el instalador. Si cambiás el modelo de Git y querés ver la nueva revisión, volvé a importar el archivo. El editor de 6.8.0 puede transformar detalles al volver a exportar un modelo, así que mantené el XML de Git como fuente para ejecutar las pruebas.

### Comprobar la importación por CMD

Usá estos comandos desde `flowable-cursos`. El archivo de cookies queda en la carpeta temporal de Windows.

```bat
curl.exe -i -c "%TEMP%\cursos-modeler.cookies" -d "j_username=admin&j_password=test&submit=Login" "http://localhost:8082/flowable-ui/app/authentication"
curl.exe -i -b "%TEMP%\cursos-modeler.cookies" -F "file=@..\proyectoGestion2026\src\main\resources\processes\organizacion-cursos.bpmn20.xml" "http://localhost:8082/flowable-ui/modeler-app/rest/import-process-model"
```

El segundo comando responde con un JSON que identifica el modelo importado. En Postman se puede hacer lo mismo con la autenticación por sesión y **Body → form-data**, clave `file` de tipo **File**. Importá una vez desde la UI o desde una petición para evitar duplicar tarjetas.

## Punto 2 y 3.3 — Personal, grupos y permisos

| Grupo | Cuenta | Qué puede operar en el proceso |
|---|---|---|
| Administración | `jefeAdmin` | Propuesta, revisión, archivo de solicitudes, informe a la empresa y archivo final. También puede reclamar las tareas de su grupo. |
| Administración | `adminCursos` | Tareas compartidas de administración. La lista definitiva queda asignada a quien el listener elija. |
| Docentes | `jefeDocente` | **Definir docente** y **Elaborar informe**. Puede elegirse a sí mismo para dictar. |
| Docentes | `docente1`, `docente2` | **Desarrollar curso** cuando quedan asignados y llega la fecha. |

Los IDs internos de los grupos son `administracion` y `docentes`. Comprobá los cinco usuarios y las dos membresías desde **IDM → Users / Groups**, ingresando como `admin`. Para operar una tarea, cerrá la sesión técnica, entrá con la cuenta indicada y abrí **Task**. Las tareas directas aparecen entre las asignadas. En tareas compartidas, buscá **Claim** antes de **Complete**. Para una verificación negativa, iniciá sesión como `docente1` y comprobá que **Preparar materiales** no aparece como tarea reclamable. El control también se ejecuta en el motor si se intenta reclamar o completar por API.

## Postman

### Configuración y dos tipos de autenticación

Guardá `ui = http://localhost:8082/flowable-ui` en un entorno de Postman y seleccionalo arriba a la derecha.

- **Arrancar una solicitud, enviar mensajes y consultar el motor.** Usá `{{ui}}/process-api/...` con **Basic Auth** `admin` / `test` en la demo local. El usuario técnico sirve para comunicaciones y diagnóstico.
- **Reclamar y completar tareas.** Hacé `POST {{ui}}/app/authentication`, **Authorization → No Auth**, **Body → x-www-form-urlencoded**, con `j_username` de la persona, `j_password` y `submit=Login`. Postman guarda la cookie de sesión en su cookie jar. Para las solicitudes `{{ui}}/app/rest/...` usá **No Auth** y dejá que Postman envíe la cookie. Para cambiar de persona, borrá la cookie de localhost en **Cookies**, o cerrá la sesión y volvé a enviar el login. Usá la contraseña de `CURSOS_PERSONAL_PASSWORD` para los usuarios de demostración recién creados.

### 1. Iniciar una solicitud y obtener tareas

**POST** `{{ui}}/process-api/runtime/process-instances`. En **Body → raw → JSON**:

```json
{
  "message": "Solicitud de curso",
  "businessKey": "PRUEBA-23-01",
  "variables": [
    {"name": "empresaCodigo", "type": "string", "value": "EMP-RC"},
    {"name": "empresaNombre", "type": "string", "value": "Capacitación Río Cuarto"},
    {"name": "cursoNombre", "type": "string", "value": "Modelado BPMN"},
    {"name": "cursoDescripcion", "type": "string", "value": "Capacitación para el equipo administrativo"}
  ]
}
```

Copiá `id` de la respuesta como variable `instanciaId`. Hacé **GET** `{{ui}}/process-api/runtime/tasks?processInstanceId={{instanciaId}}`. Copiá el ID de la tarea indicada en el recorrido. Puede agregarse `taskDefinitionKey` en **Params** para filtrar. Para el primer paso la clave es `Propuesta` y la persona asignada es `jefeAdmin`.

### 2. Reclamar y completar

| Operación | Solicitud con sesión de usuario | Cuerpo |
|---|---|---|
| Reclamar una tarea compartida | **PUT** `{{ui}}/app/rest/tasks/{{tareaId}}/action/claim` | JSON `{}`. Usa al usuario autenticado como nuevo responsable. |
| Completar una tarea sin formulario | **PUT** `{{ui}}/app/rest/tasks/{{tareaId}}/action/complete` | JSON `{}`. |
| Leer el formulario de una tarea | **GET** `{{ui}}/app/rest/task-forms/{{tareaId}}` | Copiá la propiedad `id` del formulario como `formId`. |
| Enviar el formulario | **POST** `{{ui}}/app/rest/task-forms/{{tareaId}}` | JSON con `formId` y `values` como en el ejemplo. |

Para **Propuesta** o **Revisar propuesta**, con sesión de `jefeAdmin`:

```json
{
  "formId": "{{formId}}",
  "values": {
    "programa": "Introducción a BPMN",
    "presupuesto": 15000,
    "fechasPosibles": "Martes y jueves por la mañana"
  }
}
```

Verificá la denegación con la sesión de `adminCursos` intentando completar **Propuesta** o con `docente1` intentando reclamar **Material**. Debe rechazarse con `403` y conservarse la tarea. Con la sesión correcta, al completarla debe desaparecer de las pendientes. También se comprueba el permiso en la API técnica si se intenta un `POST /process-api/runtime/tasks/{id}` con `{"action":"complete"}` desde la cuenta `admin`.

### 3. Responder y verificar temporizadores

Para una comunicación, con Basic Auth técnico:

1. Hacé **GET** `{{ui}}/process-api/runtime/executions`. En **Params** poné `processInstanceId = {{instanciaId}}` y `messageEventSubscriptionName` con el nombre del mensaje.
2. Copiá `data[0].id` como `ejecucionId`. Hacé **PUT** `{{ui}}/process-api/runtime/executions/{{ejecucionId}}`. Enviá este cuerpo para aceptar. Elegí una fecha cercana real con zona horaria.

   ```json
   {
     "action": "messageEventReceived",
     "messageName": "Aceptación",
     "variables": [{"name": "fechaCurso", "type": "string", "value": "2027-01-15T10:00:00-03:00"}]
   }
   ```

Los otros mensajes usan el mismo procedimiento. **Rechazo** y **Cancelación** pueden ir sin `variables`. **Solicitud de modificaciones** lleva `observaciones` de tipo string. **Nómina de participantes** lleva `nomina` de tipo string con una lista JSON, por ejemplo `["Ana","Pablo"]` como texto.

Al aceptar, comprobá en **Task** y por `GET /runtime/tasks` que están **Preparar materiales** y **Evaluar recursos** en paralelo. Con `adminCursos`, reclamá y completá materiales. Reclamá y enviá Evaluar recursos con `values = {"requiereAula":true,"requierePlataforma":false,"requiereEquipamiento":false}`. Entregá la nómina y mirá el `assignee` de **Confeccionar lista definitiva**. En una base limpia y con cargas iguales el desempate por ID elige `adminCursos`. Si `adminCursos` tiene más tareas pendientes asignadas que `jefeAdmin`, se elige `jefeAdmin`. Completá la lista con esa sesión.

Con `jefeDocente`, enviá **Definir docente** con `values = {"docenteAsignado":"docente1"}`. Consultá **GET** `{{ui}}/process-api/management/timer-jobs?processInstanceId={{instanciaId}}` para ver el vencimiento; antes de la fecha no aparece **Desarrollar curso**. Al llegar el plazo, entrá como `docente1` y completá esa tarea. Con `jefeDocente` enviá **Elaborar informe**, por ejemplo `values = {"informeResultados":"Se dictó el curso según el programa acordado. Los participantes completaron la práctica final."}`. Con `jefeAdmin` completá **Enviar informe y archivar expediente**.

## Puntos 3.1 a 3.5 — Verificación y resultados

| Requisito | Qué buscar en Flowable y Postman | Qué verificar por CMD |
|---|---|---|
| **3.1 Viabilidad** | En una base de prueba sin otras instancias de cursos, iniciá seis solicitudes sin cerrar las anteriores. Las cinco primeras tienen **Propuesta** en Task, la sexta termina. Buscá `totalCursosVigentes` y `resultado` en el historial. | Las pruebas Maven verifican `4 → cursoViable`, `5 → NO_VIABLE` y conteo entre dos versiones. En PostgreSQL, la fila de la sexta muestra `NO_VIABLE`. |
| **3.2 Base institucional** | Al iniciar, guardá el `instanciaId` de la respuesta REST. Usá ese ID para consultar la misma instancia en Flowable Admin. | Consultá `empresas` y `cursos`. La fila se vincula por `proceso_id`, con empresa, nombre, descripción, propuesta, presupuesto, fechas e informe. |
| **3.3 Usuarios y grupos** | Verificá los cinco en IDM. En Task o Postman iniciá sesión como administrativo y como docente con la misma credencial de Flowable para probar los permisos. | El instalador crea ambas membresías; al repetirlo evita duplicados. |
| **3.4 Listeners** | Consultá `assignee` de **Lista** y probá otra instancia con diferente carga. Buscá `resultado` histórico al cerrar. | Las pruebas Maven validan las asignaciones y los cinco resultados. En la consulta SQL, el estado debe coincidir con el resultado de Flowable. |
| **3.5 Datos y temporizadores** | Consultá variables durante el proceso con **GET** `{{ui}}/process-api/runtime/process-instances/{{instanciaId}}/variables`. Probá una revisión y comprobá que `intentosRecordatorio` vuelve a cero. Para probar silencio rápido, iniciá con `plazoRespuesta` de tipo string con valor `PT5S`. | Las pruebas adelantan el reloj del motor para comprobar dos intentosRecordatorio, archivo en el tercero y aparición de **Dictar** a la fecha acordada. |

Historial en Postman: **GET** `{{ui}}/process-api/history/historic-variable-instances?processInstanceId={{instanciaId}}&variableName=resultado`. En Flowable Admin podés localizar la instancia por su ID y ver su historial. Caminos esperados:

- `FINALIZADO`: confirmá, dictá, elaborá informe, archivá y terminá la liberación de los recursos.
- `CANCELADO`: durante la preparación enviá **Cancelación**. Si contrataste equipo, reclamá y completá la tarea de liberación. Luego completá **Verificar compensaciones** como `jefeAdmin`. El resultado llega al salir por la rama de cancelación. [Prueba completa](Predefinitiva.md#5-verificar-compensaciones-y-la-salida-por-xor).
- `RECHAZADO`: enviá **Rechazo** y archivá la solicitud.
- `SIN_RESPUESTA`: enviá la propuesta, dejá pasar dos intentosRecordatorio y archivá cuando venza la tercera espera.
- `NO_VIABLE`: iniciá una solicitud mientras cinco anteriores siguen abiertas.

### Operaciones del proceso por CMD

Podés comprobar el mismo flujo desde CMD. Iniciá una solicitud y copiá el `id` devuelto:

```bat
set "UI=http://localhost:8082/flowable-ui"
curl.exe -i -u admin:test -H "Content-Type: application/json" -d "{\"message\":\"Solicitud de curso\",\"businessKey\":\"PRUEBA-CMD\"}" "%UI%/process-api/runtime/process-instances"
set "INSTANCIA=PEGAR_ID_DE_LA_INSTANCIA"
curl.exe -sS -u admin:test "%UI%/process-api/runtime/tasks?processInstanceId=%INSTANCIA%"
curl.exe -sS -u admin:test "%UI%/process-api/runtime/process-instances/%INSTANCIA%/variables"
curl.exe -sS -u admin:test "%UI%/process-api/management/timer-jobs?processInstanceId=%INSTANCIA%"
```

Para comprobar un formulario con la sesión de su responsable, copiá el ID de **Propuesta**. En el login reemplazá `TU_CLAVE_LOCAL` por la contraseña local de `jefeAdmin`. Después copiá el ID del formulario que devuelve el GET:

```bat
set "TAREA=PEGAR_ID_DE_LA_TAREA"
curl.exe -i -c "%TEMP%\cursos-jefe.cookies" --data-urlencode "j_username=jefeAdmin" --data-urlencode "j_password=TU_CLAVE_LOCAL" -d "submit=Login" "%UI%/app/authentication"
curl.exe -sS -b "%TEMP%\cursos-jefe.cookies" "%UI%/app/rest/task-forms/%TAREA%"
set "FORMULARIO=PEGAR_ID_DEL_FORMULARIO"
curl.exe -i -b "%TEMP%\cursos-jefe.cookies" -H "Content-Type: application/json" -d "{\"formId\":\"%FORMULARIO%\",\"values\":{\"programa\":\"BPMN\",\"presupuesto\":10000,\"fechasPosibles\":\"Proxima semana\"}}" "%UI%/app/rest/task-forms/%TAREA%"
```

La verificación por CMD usa los mismos mensajes y métodos de la sección Postman. Para consultar la espera actual y, al terminar, el resultado:

```bat
curl.exe -sS -u admin:test --get --data-urlencode "processInstanceId=%INSTANCIA%" "%UI%/process-api/runtime/executions"
curl.exe -sS -u admin:test "%UI%/process-api/history/historic-variable-instances?processInstanceId=%INSTANCIA%&variableName=resultado"
```

### Consultas SQL y pruebas por CMD

Desde `flowable-cursos`, con el usuario por defecto `flowable`:

```bat
docker compose exec -T database psql -U flowable -d institucion_cursos -c "SELECT codigo, nombre FROM empresas ORDER BY codigo;"
docker compose exec -T database psql -U flowable -d institucion_cursos -c "SELECT proceso_id, empresa_codigo, nombre, programa, presupuesto, fechas_posibles, fecha_inicio, informe, estado FROM cursos ORDER BY revision DESC;"
```

Esperá unos segundos si el trabajador todavía está sincronizando. Para procesar pendientes manualmente:

```bat
docker compose exec -T flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.ProyeccionInstitucional
```

Para correr las pruebas automatizadas, desde la raíz del repositorio usá `cd proyectoGestion2026` y `mvn test` con Maven en el PATH, o **IntelliJ → Maven → Lifecycle → test**. El resultado esperado es **10 pruebas, 0 fallos**. Esas pruebas usan H2. Las consultas SQL usan las dos bases PostgreSQL de la instalación.

## Qué hace cada archivo

- **`flowable-cursos/sql/` y Compose.** Crean `institucion_cursos` como una base PostgreSQL distinta de la del motor. Las tablas `empresas` y `cursos` guardan los datos del negocio y su relación. Los guiones SQL son repetibles y el inicializador se ejecuta antes de arrancar la UI.
- **`EstadoCurso.java`.** El listener de inicio registra el alta. Tres listeners de flujo actualizan propuesta, fecha e informe. El listener de fin registra uno de los cinco resultados al cerrar efectivamente la instancia. Escribe en `cursos_pendientes` usando la conexión de la transacción de Flowable, por lo que un avance fallido tampoco deja una actualización pendiente.
- **`ProyeccionInstitucional.java` y `ConfiguracionInstitucional.java`.** Sincronizan la cola hacia la base institucional cada dos segundos. Si el destino falla, conservan la fila para reintentar. Si se repite una entrega, la clave `proceso_id` y la revisión evitan duplicar el curso o regresar su estado. La primera fila institucional aparece tras la primera entrega del trabajador.
- **`TareasCurso.java`.** Reutiliza el reparto de carga del ejemplo de la cátedra y valida el usuario autenticado. Una tarea con candidato solo admite reclamo por un integrante para sí mismo. Una tarea directa solo puede completarla su responsable. Las asignaciones de jefaturas, docentes y grupos están en el BPMN.
- **`forms/*.form` e `InstalarCursos.java`.** Agregan a Flowable Task los formularios de propuesta, recursos, selección de docente e informe. El informe textual se guarda en la base institucional. La generación o carga del PDF y las aplicaciones externas corresponden al punto 4.
- **`viabilidad-curso.dmn` y temporizadores BPMN.** Se conservaron las reglas verificadas del punto 1 y quedaron integradas con los datos e historial institucional del punto 3. Nómina y recursos permanecen como variables en Flowable.
