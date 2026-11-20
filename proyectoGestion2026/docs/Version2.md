# Version2 — Revisión de los adjuntos e integración con Flowable

El [informe de refactorización](Refactorizacion-ramaSol.md) registra los nombres actuales en `ramaSol`. La revisión integrada continúa en [Predefinitiva.md](Predefinitiva.md), con el arranque, las credenciales, las pruebas y el informe de cumplimiento.

Esta rama parte de `version1` en `caa9880`. El archivo actualizado `docs/diagrama_cursos.bpmn` usa identificadores generados por Camunda Modeler y extensiones Zeebe (Camunda Cloud). Representa el flujo de negocio y queda como referencia. El modelo ejecutable para la instalación es `src/main/resources/processes/organizacion-cursos.bpmn20.xml`. Conserva la clave `Process_Curso`, sus mensajes y sus IDs de actividad para mantener compatibilidad con las definiciones e historial ya desplegados. En Flowable Modeler importá ese XML para ver la implementación. La [guía anterior](Puntos-2-y-3.md#ver-el-diagrama-en-flowable-modeler) muestra los botones.

## Revisión individual de los 20 Java adjuntos

| Adjunto | Función y punto | Integración en este repositorio |
|---|---|---|
| `EvaluarViabilidadListener.java` | Consulta instancias del mismo proceso, excluye la actual y deja la entrada al DMN. **1.1 y 3.1**. | `OperacionesCurso` ya lo hace bajo la clave real y usa un bloqueo PostgreSQL para el último cupo. Se conserva el DMN. |
| `IniciarCursoListener.java` | Inicializa el plazo del recordatorio. **1.1 y 3.5**. | `OperacionesCurso` conserva `plazoRespuesta`, el valor `P5D` y la variante corta para pruebas. |
| `AsignarAdministradorListener.java` | Asigna la lista definitiva al administrativo con menor carga. **2 y 3.4**. | Se conserva `TareasCurso` con desempate por ID y cumplimiento del reclamo y del responsable. |
| `DatosCursoListener.java` | Valida propuesta, recursos, docente e informe y sincroniza sus datos. **3.2, 3.4, 3.5, 4.1.3 y 4.1.4**. | Las validaciones viven en `TareasCurso`. `EstadoCurso` recibe el tipo de documento desde el BPMN, encola los datos y el proyector guarda el PDF. Las variables del adjunto se adaptaron a `fechaCurso`, `docenteAsignado`, `nomina` y las tres opciones booleanas de recursos. |
| `ActualizarEstadoCursoListener.java` | Guarda el resultado al terminar. **3.4**. | `EstadoCurso` mantiene los cinco estados y actualiza por ID de instancia. Verifica el cierre efectivo antes de encolar el resultado. |
| `ReservarAulaDelegate.java` | Simula reserva y error de falta de aulas. **1.2, 1.3, 3.5 y 4.1.3**. | `OperacionesCurso` usa `aulaDisponible`, genera `idReservaAula` y conserva `AULA_NO_DISPONIBLE` para permitir corregir el intento. |
| `LiberarAulaDelegate.java` | Libera el aula al ejecutar la compensación. **1.3 y 1.4**. | Se conserva el delegado de `OperacionesCurso` asociado al manejador de compensación del BPMN. |
| `HabilitarEmpresaDelegate.java` | Activa una cuenta de empresa tras la aceptación. **4.1.2**. | `OperacionesCurso` fija `accesoEmpresaHabilitado` y la proyección activa la cuenta por código de empresa sin reemplazar su clave. `CuentaEmpresa` permite simular la entrega inicial de credenciales desde CMD. |
| `ProbarDelegatesMain.java` | Programa de ensayo para reserva, error y compensación. **1.2–1.4 y defensa**. | Sus casos equivalentes se prueban en `OrganizacionCursosTest`, con los IDs y variables de este BPMN. |
| `ProbarListenersMain.java` | Ejecuta no viable, rechazo, modificaciones, tiempo agotado, cancelación y recorrido normal. **1, 3.4, 3.5 y defensa**. | Los escenarios se verifican en JUnit con H2 y reloj controlado. Se añade una prueba de referencia única para las solicitudes web. |
| `ServidorCursos68.java` | Ensambla REST, seguridad, usuarios y despliegue en el proyecto de origen. **3.3 y 4.1**. | En `ramaSol`, `servidor_Institucional.java` configura el portal dentro de la UI de Flowable 6.8.0. `InstalarCursos` sigue desplegando el BPMN y los recursos. |
| `MotorRest.java` | Cliente HTTP y paginado de la API de Flowable. **4.1 y 4.2.2**. | En `ramaSol`, `ClienteRestFlowable.java`. Usa el contexto `/flowable-ui`. La cuenta técnica envía mensajes y consulta. Para actuar sobre tareas se usa una sesión de Flowable del usuario real. |
| `PermisosRest.java` | Valida permisos antes de operar sobre las tareas por REST. **2, 4.1.3 y 4.1.4**. | En `ramaSol`, `CamposPermitidosTarea.java`. El portal valida disponibilidad y limita los campos por tipo de tarea. Se mantiene la validación del motor en `TareasCurso`. El personal usa su cuenta y la API de Task; la API técnica sigue reservada para integración. |
| `SeguridadCursos.java` | Autentica personal y empresas. **3.3 y 4.1.2–4.1.4**. | En `ramaSol`, `AutenticacionPortalCursos.java`. Usa IDM para personal y BCrypt en PostgreSQL para empresas. La seguridad se aplica a la ruta del portal y conserva la autenticación nativa de Flowable. |
| `Institucion.java` | Acceso a datos, activación de empresas y PDFs. **3.2, 4.1.1–4.1.3**. | En `ramaSol`, `RepositorioInstitucional.java`. Se adapta a `empresas` y `cursos`. El alta y los documentos se sincronizan mediante la cola transaccional. `DocumentosCurso` genera los PDF y la tabla `documentos_curso` conserva las revisiones. |
| `PortalCursos.java` | Expone solicitud pública, tareas por rol, seguimiento y documentos. **4.1.1–4.1.4**. | En `ramaSol`, `ControladorPortalCursos.java`. Integrado con un portal HTML adaptable a pantallas pequeñas. Se valida identidad, propiedad de los cursos y datos permitidos antes de operar. |
| `ConexionMotor.java` | Abre el motor Java auxiliar con la base de Flowable. **4.2.1 y 4.3**. | En `ramaSol`, `ConexionFlowable.java`. Usa PostgreSQL del Compose, publicación local 5433 y variables `CURSOS_JDBC`, `CURSOS_BD_USUARIO` y `CURSOS_BD_CLAVE`. La UI sigue ejecutando los temporizadores. |
| `ServicioMateriales.java` | Identifica administrativos, lista, reclama y completa materiales. **4.2.1**. | En `ramaSol`, `GestionTareasMateriales.java`. Adaptado a `Process_Curso` y `Material`. Envía el usuario autenticado al listener del motor tanto al reclamar como al completar. |
| `AppMateriales.java` | Interfaz de escritorio Swing. **4.2.1**. | En `ramaSol`, `VentanaMaterialesCurso.java`. Reutiliza el servicio Java y muestra las tareas que se ven en la misma base de Flowable. |
| `AnalisisHistorico.java` | Resultados y porcentajes, promedios de las tres ramas y distribución por usuario. **4.3.1–4.3.3**. | En `ramaSol`, `InformeHistoricoCursos.java`. Usa `Process_Curso`, `resultado` y los IDs actuales. Excluye actividades interrumpidas y mide inscripciones desde `Nomina` hasta `Lista`. |

`SimularComunicaciones` agrega la opción **4.2.2** por consola, prevista por el enunciado para grupos de hasta tres personas. Reutiliza `ClienteRestFlowable` y lista solo las suscripciones externas que están activas para la solicitud elegida. La minería del punto **4.4** tiene una salida distinta de las consultas del 4.3 y queda fuera de esta integración de adjuntos.

## Qué cambia en archivos y datos

- **Compose, Dockerfile y `pom.xml`** incorporan la migración `sql/03-portal.sql`, PDFBox y las dependencias de la UI de Flowable. El nombre del volumen de `version1` se conserva. La migración se puede repetir.
- **BPMN, `EstadoCurso`, `OperacionesCurso`, `ProyeccionInstitucional` y `DocumentosCurso`** agregan registro único de solicitud web, seguimiento, activación de acceso y documentos. La cola se actualiza junto con la transacción del motor. Si el destino falla, la revisión queda en la cola para reintentar. Los PDF se generan al enviar cada versión de la propuesta y al completar el informe.
- **`servidor_Institucional`, `AutenticacionPortalCursos`, `CamposPermitidosTarea`, `ClienteRestFlowable`, `ControladorPortalCursos`, `RepositorioInstitucional`, `CuentaEmpresa` y `resources/portal/`** agregan la pantalla pública, el panel por rol y la API del portal. La sesión técnica se usa en el servidor. El navegador usa la credencial de la persona autenticada.
- **`auxiliares/`** incorpora la app Swing de materiales, el simulador por consola y el informe histórico. **`OrganizacionCursosTest`** verifica el flujo, la autorización y la referencia única de solicitud.

## Arranque en Windows

En **CMD**, desde la raíz del repositorio:

```bat
git fetch origin
git switch --track origin/version2
cd flowable-cursos
dir /a /b .env*
docker compose config -q
docker compose up -d --build
docker compose ps -a
docker compose logs preparar-institucion
```

Si `version2` ya existe localmente, usá `git switch version2` y `git pull --ff-only origin version2`. Conservá `.env`. Si falta, copiá `.env.example` a `.env` y completá las dos contraseñas requeridas. Si la cuenta técnica de tu Flowable cambió, definí `CURSOS_REST_USUARIO` y `CURSOS_REST_CLAVE` en tu `.env`. Los valores iniciales del Compose son `admin` / `test` para el entorno local.

Esperá **healthy** para `database`, **Exited (0)** para `preparar-institucion` y **Up** para `flowable`. Si el migrador falla, leé sus logs. Un error de contraseña en un volumen existente se resuelve usando las credenciales con las que se creó ese volumen.

```bat
docker compose exec -T flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.InstalarCursos
curl.exe -i -u admin:test "http://localhost:8082/flowable-ui/process-api/repository/process-definitions?key=Process_Curso&latest=true"
```

El primer comando debe mostrar `Process_Curso disponible`. El segundo debe devolver **200** y el proceso. Cada caso de prueba de esta versión empieza con una solicitud nueva. El despliegue no migra instancias ya iniciadas.

## Usuarios y contraseñas de la demo

| Dónde | Usuario | Contraseña |
|---|---|---|
| Flowable UI → IDM, Modeler o Admin | `admin` | `test` en la instalación local inicial. Si cambiaste la clave en Flowable, usá la actual. |
| Portal → **Acceso al portal**, Flowable UI → **Task**, y Postman → `/cursos/api/yo` o `/cursos/api/tareas` | `jefeAdmin`, `adminCursos`, `jefeDocente`, `docente1` o `docente2` | Al crearlos, `InstalarCursos` usa `CURSOS_PERSONAL_PASSWORD` de `flowable-cursos/.env`. Si el usuario ya existía, conserva su clave en Flowable. |
| Portal → **Acceso al portal**, y Postman → `/cursos/api/empresa/cursos` | `empresa-EMP-PRUEBA` si el código fue `EMP-PRUEBA` | La que asignás con `CuentaEmpresa EMP-PRUEBA` después de aceptar la propuesta. Cada empresa tiene su propia cuenta. |
| Portal → **Solicitar una capacitación**, y Postman → `POST /cursos/api/solicitudes` | Sin usuario | Sin contraseña. En Postman elegí **No Auth**. |
| Postman → `/process-api/...`, servidor del portal y simulador de comunicaciones | `CURSOS_REST_USUARIO`, por defecto `admin` | `CURSOS_REST_CLAVE`, por defecto `test`. Son las credenciales de una cuenta técnica existente de Flowable. |
| PostgreSQL y conexión de los auxiliares Java | `POSTGRES_USER`, por defecto `flowable` | `POSTGRES_PASSWORD` de `flowable-cursos/.env`. Al ejecutar los auxiliares desde Windows, pasá estos valores como `CURSOS_BD_USUARIO` y `CURSOS_BD_CLAVE`. |
| Ventana Swing de **VentanaMaterialesCurso** | `adminCursos` o `jefeAdmin` | La clave actual del usuario en Flowable. La conexión a PostgreSQL se configura aparte. |

Para la primera propuesta, ingresá al portal como `jefeAdmin`. `adminCursos` puede reclamar tareas compartidas de Administración. `jefeDocente` define quién dicta y elabora el informe. `docente1` y `docente2` ven **Desarrollar curso** cuando les corresponde por asignación y fecha.

El formulario del portal envía las credenciales por HTTP Basic a `/cursos/api/yo`. Si el navegador abre su propio cuadro de autenticación después de pulsar **Ingresar**, cancelalo y revisá el usuario y la clave. Una respuesta **401** indica que la API rechazó la autenticación. Si abrís directamente una URL protegida de la API en el navegador, el cuadro es una forma esperada de pedir la misma credencial. Para verificarla en Postman, usá **GET** `http://localhost:8082/flowable-ui/cursos/api/yo` con **Basic Auth**. La respuesta esperada es **200** y el rol `PERSONAL` o `EMPRESA`.

## Recorrido de prueba en el navegador

1. Abrí [Portal de capacitación](http://localhost:8082/flowable-ui/cursos/) y enviá una solicitud completa, por ejemplo para `EMP-PRUEBA`. Anotá el ID que aparece. Entrá como `jefeAdmin` con la contraseña que usás en Flowable, abrí **Registrar y elaborar propuesta** y completala. El portal usa la API de Flowable para esa misma instancia. En una solicitud viable, esa tarea desaparece y quedan activas las esperas de negociación.
2. Enviá **Solicitud de modificaciones** con el simulador. Volvé al portal y completá **Revisar propuesta**. Eso genera la segunda versión del PDF. Luego enviá **Aceptación** con una fecha y hora ISO 8601 con zona, por ejemplo `2027-01-15T10:00:00-03:00` si corresponde al día de tu prueba.
3. En **CMD**, desde `flowable-cursos`, esperá unos segundos y habilitá la entrega simulada de credenciales para la empresa:

   ```bat
   docker compose exec flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.CuentaEmpresa EMP-PRUEBA
   ```

   Ingresá una clave de 10 a 72 bytes en el prompt. Luego entrá al portal como `empresa-EMP-PRUEBA` con esa clave. Esperá ver la solicitud en preparación, con actividades pendientes de sus ramas paralelas. Si aparece **La empresa todavía no tiene acceso habilitado**, comprobá que se recibió **Aceptación**, que usaste el código de la solicitud y que la sincronización terminó. Para otro curso de esa empresa se reutiliza la cuenta; no repitas el comando si querés conservar la clave previa.
4. Completá **Preparar materiales** con la aplicación Swing. En el portal, `adminCursos` puede reclamar **Evaluar recursos**, marcar por lo menos un recurso y confirmar. Enviá la nómina con el simulador. Completá la lista con el administrativo que indique el responsable. Para equipamiento, aula virtual o liberación, usá [Flowable UI](http://localhost:8082/flowable-ui/) → **Task** con el usuario correspondiente.
5. Cuando terminen las tres ramas, ingresá al portal como `jefeDocente`, abrí **Definir docente** y elegí `docente1` o el usuario que estés probando. A la fecha acordada aparece **Desarrollar curso** para esa persona. Completala con su sesión. Como `jefeDocente`, escribí el informe. Como `jefeAdmin`, abrí **Enviar informe y archivar expediente**, descargá el PDF y completá la tarea. Esperá que el portal de la empresa muestre `FINALIZADO` tras liberar los recursos. Si el PDF aún devuelve **404** justo al crear el informe, esperá la sincronización y reintentá.
6. Para comprobar separación de datos, creá otra solicitud para otra empresa. La primera cuenta debe ver únicamente sus cursos aceptados.

## Pruebas exactas en Postman

Guardá `ui=http://localhost:8082/flowable-ui` en un entorno. Para la API del portal, las rutas empiezan con `{{ui}}/cursos/api`. Las llamadas al motor usan `{{ui}}/process-api`.

**POST** `{{ui}}/cursos/api/solicitudes`, **No Auth**, **Body → raw → JSON**:

```json
{
  "solicitudId": "5dc4ad23-a96d-4162-8443-b686b5933dea",
  "empresaCodigo": "EMP-PRUEBA",
  "empresaNombre": "Empresa de prueba",
  "contacto": "Sol",
  "email": "sol@example.com",
  "telefono": "3581234567",
  "cursoNombre": "Modelado BPMN",
  "cursoDescripcion": "Capacitación para el equipo",
  "participantesEstimados": 10,
  "fechasPreferidas": "Lunes por la mañana"
}
```

Esperá **201** con `processInstanceId`. Repetir el mismo JSON devuelve el mismo ID. Cambiar los datos manteniendo el UUID da **409**. Para una solicitud distinta, cambiá el UUID.

| Objetivo | Método y ruta desde `{{ui}}` | Autenticación, cuerpo y resultado |
|---|---|---|
| Verificar identidad | **GET** `/cursos/api/yo` | **Basic Auth** con la persona o empresa. Esperá `PERSONAL` o `EMPRESA`; contraseña incorrecta da **401**. |
| Ver tareas | **GET** `/cursos/api/tareas` | Basic Auth del personal. Buscá el `taskDefinitionKey` e `id`. Si la lista está vacía, consultá el avance en Flowable Admin y confirmá que usás al responsable. |
| Reclamar | **POST** `/cursos/api/tareas/{{tareaId}}/reclamar` | Basic Auth de un administrativo, JSON `{}`. Esperá **204** y el usuario en `assignee`. |
| Completar propuesta | **POST** `/cursos/api/tareas/{{tareaId}}/completar` | Basic Auth de `jefeAdmin`, JSON `{"programa":"BPMN","presupuesto":15000,"fechasPosibles":"Lunes por la mañana"}`. Esperá **204**. Si usás el ID de una tarea ajena, **403**. |
| Decidir recursos o docente | El mismo **POST** con el ID correspondiente | Recursos usa `{"requiereAula":true,"requierePlataforma":false,"requiereEquipamiento":false}`. Docentes usa `{"docenteAsignado":"docente1"}`, con sesión de `jefeDocente`. |
| Completar informe y archivo | El mismo **POST** con el ID correspondiente | Informe usa `{"informeResultados":"El equipo completó la práctica."}` y la sesión de `jefeDocente`. Archivo usa `{}` y la sesión de `jefeAdmin`. |
| Acceder a PDF | **GET** `/cursos/api/documentos/{{instanciaId}}/PROPUESTA` o `/INFORME` | Basic Auth de `jefeAdmin` para la prueba. Esperá **200**, tipo `application/pdf`. La API devuelve la última revisión. |
| Ver cursos propios | **GET** `/cursos/api/empresa/cursos` | Basic Auth de `empresa-EMP-PRUEBA`. Esperá solo los cursos aceptados para ese código y sus actividades pendientes. |

Para enviar comunicaciones manualmente en Postman, usá Basic Auth de la cuenta técnica. **GET** `/process-api/runtime/executions` con **Params** `processInstanceId` y `messageEventSubscriptionName`. Copiá `data[0].id` como `ejecucionId`. Hacé **PUT** `/process-api/runtime/executions/{{ejecucionId}}` con `{"action":"messageEventReceived","messageName":"Rechazo","variables":[]}` para rechazo, por ejemplo. Para aceptación agregá una variable `fechaCurso` de tipo `string`. Para modificaciones usá `observaciones`. Para nómina, `nomina` como texto JSON. El simulador de CMD hace estas mismas llamadas. Si `data` está vacío, el proceso no está esperando ese mensaje.

## Pruebas desde CMD de los auxiliares y de PostgreSQL

Para Maven, abrí **otra** ventana CMD y ubicáte en `proyectoGestion2026`. Necesitás Maven en el PATH y JDK 11 o superior. Podés lanzar las clases desde IntelliJ si usás Maven integrado.

```bat
mvn test
set "CURSOS_BD_USUARIO=flowable"
set "CURSOS_BD_CLAVE=TU_POSTGRES_PASSWORD"
set "CURSOS_JDBC=jdbc:postgresql://localhost:5433/flowable"
mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.VentanaMaterialesCurso
```

- **mvn test** verifica el motor en memoria. Buscá **11 pruebas, 0 fallos**.
- **VentanaMaterialesCurso** abre la ventana Swing. Entrá como `adminCursos`, pulsá **Actualizar**, seleccioná **Preparar materiales**, pulsá **Reclamar** y **Completar**. La tarea pasa de disponible a asignada y sale de pendientes tanto en la app como en **Flowable Task**. Si la conexión falla, revisá `docker compose ps`, las credenciales y que la base del motor sea la indicada en la URL.

En la misma carpeta, para el simulador:

```bat
set "CURSOS_REST_USUARIO=admin"
set "CURSOS_REST_CLAVE=test"
mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.SimularComunicaciones
```

Elegí el número de instancia y después el número de mensaje. La consola muestra solo los mensajes habilitados. Para aceptar, escribí una fecha con hora y desfase, como `2027-01-15T10:00:00-03:00`. Si devuelve **401**, revisá la cuenta técnica. Si todavía no ofrece mensajes, completá **Propuesta** o verificá que elegiste la instancia correcta.

```bat
mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.InformeHistoricoCursos
```

Con las mismas variables PostgreSQL, muestra tres tablas. Los porcentajes usan como denominador las instancias terminadas. La preparación se mide sobre casos que alcanzaron `SubFin`; la rama de recursos espera todas las acciones seleccionadas. El conteo de tareas excluye las interrumpidas. Con historial vacío, se esperan ceros y **sin casos** en promedios. Para probar, terminá una solicitud normal, rechazá otra y cancelá una tercera durante la preparación. La cancelada debe aparecer en resultados y quedar fuera del promedio de preparación.

Desde `flowable-cursos`, podés comprobar el vínculo y el versionado de documentos:

```bat
docker compose exec -T database psql -U flowable -d institucion_cursos -c "SELECT proceso_id, empresa_codigo, nombre, estado FROM cursos ORDER BY revision DESC;"
docker compose exec -T database psql -U flowable -d institucion_cursos -c "SELECT proceso_id, tipo, COUNT(*) AS versiones FROM documentos_curso GROUP BY proceso_id,tipo ORDER BY proceso_id,tipo;"
```

La consulta de documentos debería mostrar **2 propuestas** tras una revisión y **1 informe** cuando el jefe docente lo completa. Si la sincronización todavía está pendiente, revisá `docker compose logs --tail=100 flowable` y la migración `03-portal.sql`.
