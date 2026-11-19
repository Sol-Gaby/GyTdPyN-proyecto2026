# Integración predefinitiva de Organización de cursos

## Qué integra esta revisión

Flowable ejecuta `Process_Curso` y guarda las tareas y el historial en `POSTGRES_DB`. El mismo servidor PostgreSQL contiene `institucion_cursos`, la base propia de la institución que pide el punto 3.2. Un único `institucion.sql` prepara ambas bases en el orden correcto. Se puede volver a ejecutar sobre el volumen existente. Las tablas y los datos de demostración se agregan de forma idempotente.

`TareasCurso` es el Task Listener usado por el BPMN, el portal y la aplicación de materiales a través del motor. En **Confeccionar lista definitiva**, compara tareas pendientes asignadas y desempata por ID. También controla el reclamo, el responsable y las validaciones antes de completar. `EstadoCurso` es el Execution Listener que encola la solicitud al iniciar, las revisiones y los documentos cuando corresponde, y el resultado al cerrar. Ambos listeners operan sobre las mismas instancias, incluso cuando la tarea se completa desde Flowable Task o Swing.

Para **4.1.1**, el portal valida los datos y envía el mensaje **Solicitud de curso** a la API REST de Flowable. El motor confirma en una transacción la instancia, su referencia de solicitud y el evento de la cola que lleva los datos. La API del portal confirma con **201** después de verificar la fila en `institucion_cursos.cursos`. La fila conserva el ID de Flowable en `proceso_id`, la referencia web en `solicitud_referencia` y el formulario en `solicitud_web`. Si se interrumpe la sincronización, la cola permite reintentar. Con el mismo identificador y los mismos datos, el portal recupera la instancia previa. Con los datos cambiados devuelve **409**. Los registros web anteriores que todavía no tengan la columna de referencia poblada siguen vinculados por `proceso_id`.

### Archivos tocados

Las rutas parten de `GyTdPyN-proyecto2026`.

| Ruta | Cambio |
|---|---|
| `flowable-cursos/sql/institucion.sql` | Consolida estructura del motor, estructura institucional, datos de demo y ampliaciones del portal. Agrega la referencia de solicitud y un índice para procesar la cola por instancia. Reemplaza `01-estructura.sql`, `02-datos-demostracion.sql` y `03-portal.sql` de esa misma carpeta. |
| `flowable-cursos/docker-compose.yml` | Ejecuta el guion consolidado antes de arrancar Flowable. |
| `proyectoGestion2026/src/main/java/proyecto/cursos/ConfiguracionInstitucional.java` | El portal y el sincronizador automático comparten el mismo componente de proyección. |
| `proyectoGestion2026/src/main/java/proyecto/cursos/EstadoCurso.java` | Encola la referencia web y los datos junto con el ID de la instancia. |
| `proyectoGestion2026/src/main/java/proyecto/cursos/ProyeccionInstitucional.java` | Puede procesar la cola de una solicitud concreta y persistir su referencia. El proceso periódico sigue atendiendo todos los eventos. |
| `proyectoGestion2026/src/main/java/proyecto/cursos/Institucion.java` | Verifica la existencia institucional de la solicitud. |
| `proyectoGestion2026/src/main/java/proyecto/cursos/PortalCursos.java` | Confirma la persistencia en el punto 4.1.1 y entrega al cliente la referencia y el ID. |
| `proyectoGestion2026/src/main/resources/portal/app.js` | Muestra ambos identificadores y presenta errores de autenticación en la página. |
| `proyectoGestion2026/src/main/resources/processes/organizacion-cursos.bpmn20.xml` | Documenta en el propio modelo ejecutable las responsabilidades de los dos listeners. |
| `proyectoGestion2026/src/main/java/proyecto/cursos/auxiliares/AppComunicaciones.java` | Ventana Swing para elegir una solicitud y enviar únicamente mensajes habilitados. |
| `proyectoGestion2026/src/main/java/proyecto/cursos/auxiliares/SimularComunicaciones.java` | Comparte las consultas y los envíos REST con la interfaz Swing y permite seguir usando CMD. |
| `proyectoGestion2026/src/test/java/proyecto/cursos/OrganizacionCursosTest.java` | Usa el script consolidado y comprueba que el mismo evento guarda la referencia, el formulario y el ID de instancia. |
| `proyectoGestion2026/mineria/exportar-negociacion.sql` | Exporta un CSV de los hitos concluidos de la negociación desde las tablas históricas de Flowable. |
| `proyectoGestion2026/mineria/analizar.py` | Calcula las tres variantes más frecuentes y genera un gráfico DFG con PM4Py. |
| `proyectoGestion2026/docs/Predefinitiva.md` y `proyectoGestion2026/docs/Version2.md` | Documentan la integración, el estado de cumplimiento y la ruta de prueba. |

## Informe de cumplimiento

«Implementado» indica que existe código para el requisito. El tutorial permite obtener evidencia sobre el entorno de entrega.

| Inciso | Nivel | Qué demostrar |
|---|---|---|
| 1.1 | Implementado | Viabilidad, propuesta, revisión, rechazo, hasta dos recordatorios por propuesta y archivo. |
| 1.2 | Implementado | Nómina/lista, materiales y recursos en paralelo. La selección de recursos exige al menos una alternativa. La preparación espera las tres ramas. |
| 1.3 | Implementado | Cancelación durante la preparación y compensación de los recursos efectivamente gestionados. |
| 1.4 | Implementado | Confirmación, selección de docente, temporizador por fecha pactada, dictado, informe, archivo y liberación normal. |
| 1.5 | Implementado | BPMN ejecutable, con identificadores de actividades estables y ajustes para Flowable. |
| 2 | Implementado | Dos administrativos y tres docentes. Cuentas directas, candidatos, reclamo y restricciones de finalización. |
| 3.1 | Implementado | DMN con conteo de otras instancias activas de todas las versiones de `Process_Curso`. |
| 3.2 | Implementado | Base propia en PostgreSQL, datos de demo y relación por `proceso_id`. |
| 3.3 | Implementado | Instalador de usuarios y grupos. El portal y la app de materiales validan las credenciales de Flowable. |
| 3.4 | Implementado | `TareasCurso` asigna al administrativo con menor carga. `EstadoCurso` registra uno de los cinco resultados de cierre. |
| 3.5 | Implementado | Variables de acuerdo, recursos, nómina e informe. Temporizadores parametrizados por duración y fecha. |
| 4.1.1 | Implementado en esta revisión | Formulario público, REST, acuse después de persistir, vínculo verificable en PostgreSQL e idempotencia. |
| 4.1.2 | Implementado | Habilitación al aceptar, reutilización de cuenta y consulta de cursos propios y actividades paralelas. |
| 4.1.3 | Implementado | Tareas administrativas, PDF por versión de propuesta, evaluación de recursos, archivo e informe. Las tareas especializadas de equipamiento y aula virtual se prueban en Flowable Task. |
| 4.1.4 | Implementado | Selección entre los tres docentes y confirmación del dictado con la cuenta asignada. |
| 4.2.1 | Implementado | App Swing de materiales con API Java, reclamo y finalización. |
| 4.2.2 | Implementado | Simulador Swing y consola con API REST y filtro por suscripciones activas. |
| 4.3.1 | Implementado | Cantidad y porcentaje de los cinco resultados sobre instancias terminadas. |
| 4.3.2 | Implementado | Duraciones de las tres ramas, excluyendo preparaciones canceladas. |
| 4.3.3 | Implementado | Tareas de usuario efectivamente completadas por persona. |
| 4.4 | Exportación y análisis implementados. Evidencia pendiente. | Ejecutar con casos reales de la demo, adjuntar CSV, gráfico/captura y redactar la comparación de esos resultados con el BPMN. |
| 5 | Entrega técnica preparada. Demostración pendiente. | Recorrer los once escenarios y adjuntar la evidencia de minería. |

## Tutorial en Windows

### 1. Levantar e instalar

Abrí Docker Desktop y esperá a que el motor de Linux esté listo. Abrí **CMD** en la raíz del repositorio. Si nunca configuraste el entorno, copiá `.env.example` a `.env` con `copy .env.example .env` después de entrar en `flowable-cursos`. Editá ese archivo con las contraseñas que correspondan. En un volumen que ya existe, conservá las credenciales de PostgreSQL originales.

```bat
cd flowable-cursos
docker compose config -q
docker compose up -d --build
docker compose ps -a
```

`config -q` valida la configuración y las variables. `up` compila, aplica el SQL y arranca Docker. En `ps -a` esperá **database healthy**, **preparar-institucion Exited (0)** y **flowable Up**. Si falla, mirá `docker compose logs --tail 100 preparar-institucion` o `docker compose logs --tail 100 flowable` según el servicio.

Abrí [Flowable UI](http://localhost:8082/flowable-ui/) y esperá la pantalla de ingreso. Allí la cuenta inicial de demo es `admin` con `test`. Después ejecutá en el mismo CMD:

```bat
docker compose exec -T flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.InstalarCursos
```

Debe aparecer **Process_Curso disponible**. El instalador publica el BPMN ejecutable, DMN y formularios y prepara los cinco usuarios. Las cuentas existentes conservan sus contraseñas. Consultá la [tabla de credenciales](Version2.md#usuarios-y-contraseñas-de-la-demo). En **Flowable Admin → Process Definitions** debe aparecer **Organizar curso de capacitación**. Para ver el diagrama editable, entrá a **Modeler → Business Process Models → Import** y seleccioná `proyectoGestion2026\src\main\resources\processes\organizacion-cursos.bpmn20.xml`.

### 2. Probar el formulario y el vínculo del punto 4.1.1

1. Abrí [Portal de capacitación](http://localhost:8082/flowable-ui/cursos/). En **Solicitar una capacitación**, escribí por ejemplo código `EMP-PREDEF`, nombre de empresa, contacto, correo, teléfono, temática `BPMN`, descripción, cantidad y fechas preferidas. Pulsá **Enviar solicitud**. Copiá el ID de instancia y la referencia `web-...`.
2. En **Flowable Admin**, buscá ese ID entre instancias activas. También podés verificarlo en Postman con **GET** `http://localhost:8082/flowable-ui/process-api/runtime/process-instances/ID_INSTANCIA` y **Basic Auth** de la cuenta técnica. Esperá **200** y la misma `businessKey`. Si la solicitud terminó por no viable, consultá `/process-api/history/historic-process-instances/ID_INSTANCIA`.
3. En **CMD**, desde `flowable-cursos`, verificá la fila. Reemplazá `ID_INSTANCIA` y, si cambiaste `POSTGRES_USER`, también `flowable`.

   ```bat
   docker compose exec -T database psql -U flowable -d institucion_cursos -c "SELECT proceso_id, solicitud_referencia, empresa_codigo, estado, solicitud_web FROM cursos WHERE proceso_id = 'ID_INSTANCIA';"
   ```

   Esperá una fila con el mismo ID, la misma referencia y todos los campos de la solicitud en `solicitud_web`. Al crear un caso viable, el estado inicial es `EN_TRAMITE`. Si recibís **503**, conservá el formulario y reintentá desde esa misma página. Revisá los logs del servicio Flowable si persiste.
4. En **Postman**, probá **POST** `http://localhost:8082/flowable-ui/cursos/api/solicitudes`. Elegí **No Auth → Body → raw → JSON**. Usá un UUID fijo nuevo para el primer envío.

   ```json
   {
     "solicitudId": "8b2fa7f1-5f60-4a67-bb96-8adb018e4d6c",
     "empresaCodigo": "EMP-POSTMAN",
     "empresaNombre": "Empresa de prueba",
     "contacto": "Ana Pérez",
     "email": "ana@example.com",
     "telefono": "3581234567",
     "cursoNombre": "BPMN",
     "cursoDescripcion": "Capacitación para el equipo",
     "participantesEstimados": 12,
     "fechasPreferidas": "Martes por la mañana"
   }
   ```

   Primer envío **201**. Repetición idéntica **201** con el mismo `processInstanceId`. Cambio de un campo con ese mismo UUID **409**. Usá otro UUID para una solicitud distinta. Un campo requerido ausente debe devolver **400**.

### 3. Negociar por REST y preparar en paralelo

1. En **Portal → Acceso al portal**, entrá como `jefeAdmin` con su clave de Flowable. Abrí **Registrar y elaborar propuesta**, completá programa, presupuesto y fechas, y confirmá. En Postman, **GET** `/flowable-ui/cursos/api/documentos/ID_INSTANCIA/PROPUESTA` con Basic Auth de `jefeAdmin` debe devolver **200** y un PDF cuando termine la sincronización.
2. Abrí otro **CMD** en la carpeta Maven `proyectoGestion2026`. Verificá `mvn -v`. Estos comandos abren el simulador Swing, que usa la cuenta técnica para llamar a la API REST de Flowable. Si las credenciales iniciales cambiaron, sustituí los valores de ejemplo.

   ```bat
   set "CURSOS_REST_USUARIO=admin"
   set "CURSOS_REST_CLAVE=test"
   mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.AppComunicaciones
   ```

   Para la interfaz de consola usá `proyecto.cursos.auxiliares.SimularComunicaciones` como clase principal. En la ventana, elegí la solicitud por ID, seleccioná **Solicitud de modificaciones**, ingresá observaciones y enviá. En el portal, como `jefeAdmin`, completá **Revisar propuesta**. Volvé al simulador, pulsá **Actualizar solicitudes** y enviá **Aceptación** con una fecha futura en formato ISO 8601 con zona, por ejemplo `2026-11-19T18:30:00-03:00` si todavía es futura en tu entorno. Si la lista de mensajes está vacía, comprobá que se envió la propuesta o que la instancia llegó a la espera correspondiente. El detalle de las llamadas manuales a la API nativa está en [Version2.md](Version2.md#pruebas-exactas-en-postman).
3. En el **CMD de Docker**, configurá la clave de la empresa aceptada. Cambiá el código si usaste otro. Ingresá una clave de 10 a 72 bytes cuando la terminal lo pida.

   ```bat
   docker compose exec flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.CuentaEmpresa EMP-PREDEF
   ```

   Entrá al portal como `empresa-EMP-PREDEF`. Debe ver sus cursos aceptados y la preparación activa. Si la cuenta todavía no aparece habilitada, comprobá la aceptación, el código y la sincronización. En Postman, **GET** `/flowable-ui/cursos/api/empresa/cursos` con esa cuenta debe devolver sus cursos. Probá una segunda cuenta para verificar el aislamiento.
4. En un CMD nuevo en `proyectoGestion2026`, configurá la conexión al motor y abrí la aplicación de materiales. Sustituí los valores por los de tu `.env`.

   ```bat
   set "CURSOS_JDBC=jdbc:postgresql://localhost:5433/flowable"
   set "CURSOS_BD_USUARIO=flowable"
   set "CURSOS_BD_CLAVE=TU_POSTGRES_PASSWORD"
   mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.AppMateriales
   ```

   Entrá como `adminCursos`, reclamá **Preparar materiales** y completala. Comprobá que la misma tarea desaparece de pendientes en **Flowable UI → Task**. Si falla la conexión, revisá el puerto publicado, el nombre de la base y la clave de PostgreSQL. La ventana pide la contraseña del administrativo en Flowable.
5. En el portal, como `adminCursos`, reclamá **Evaluar recursos** y seleccioná aula física, aula virtual o equipamiento. Seleccioná todas para probar la apertura de ramas. Completá las tareas de aula virtual y equipamiento desde **Flowable UI → Task** con un administrativo. La reserva física es automática. En el simulador enviá **Nómina de participantes**. Completá **Confeccionar lista definitiva** con el administrativo al que se asignó, sin reclamo. Para probar permisos en Postman, **GET** `/flowable-ui/cursos/api/tareas` con Basic Auth del usuario y un intento de completar una tarea ajena con **POST** `/flowable-ui/cursos/api/tareas/ID_TAREA/completar` y JSON `{}` debe dar **403** para un usuario de personal que no sea responsable.

### 4. Cerrar y probar caminos alternativos

1. En el portal, como `jefeDocente`, completá **Definir docente**. Al llegar la fecha, la cuenta elegida ve **Desarrollar curso**. Completá el dictado y, como `jefeDocente`, elaborá el informe. Como `jefeAdmin`, descargá el PDF y completá **Enviar informe y archivar expediente**. Si contrataste equipamiento, completá su liberación en **Flowable Task**. Esperá `FINALIZADO` en el portal de la empresa.
2. Usá una instancia nueva para cada camino. Las instancias iniciadas antes de este despliegue siguen ligadas a su definición anterior.

   | Camino | Acción | Resultado esperado |
   |---|---|---|
   | Revisión | Enviar modificaciones y completar la nueva propuesta. | Dos revisiones PDF para la misma instancia y contador reiniciado. |
   | Rechazo | Enviar **Rechazo** y completar **Archivar solicitud**. | `RECHAZADO`. |
   | Falta de respuesta | Para una prueba corta, crear por REST con `plazoRespuesta` igual a `PT30S`. Enviar propuesta, esperar tres vencimientos, luego archivar. | Dos recordatorios y `SIN_RESPUESTA`. |
   | Límite | Dejar cinco solicitudes de `Process_Curso` activas y crear otra. | La adicional termina `NO_VIABLE` y conserva su fila institucional. |
   | Cancelación | En preparación, gestionar un recurso y dejar una rama pendiente. Enviar **Cancelación**. Completar liberación de equipo si corresponde. | Se interrumpen las ramas y se compensan los recursos gestionados. Resultado `CANCELADO`. |
   | Aula no disponible | Crear por REST con `aulaDisponible` de tipo `boolean` y valor `false`. Aceptar y seleccionar aula física. | El intento falla con `AULA_NO_DISPONIBLE`. Corregí la variable a `true` y repetí. |
   | Responsables | Intentar completar una tarea con otra cuenta y luego con la cuenta correcta. | La primera operación falla. La segunda avanza respetando el reclamo o la asignación directa. |

   Para iniciar un escenario parametrizado desde Postman, hacé **POST** `/flowable-ui/process-api/runtime/process-instances` con Basic Auth de la cuenta técnica y JSON como este:

   ```json
   {
     "message": "Solicitud de curso",
     "businessKey": "PRUEBA-SIN-RESPUESTA",
     "variables": [
       { "name": "plazoRespuesta", "type": "string", "value": "PT30S" }
     ]
   }
   ```

### 5. Historial, minería y evidencia

En el **CMD de Maven** con las variables de la app de materiales, ejecutá:

```bat
mvn test
mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.AnalisisHistorico
```

El primero comprueba casos del motor, incluido el vínculo de la solicitud. El segundo muestra resultados/porcentajes, duraciones promedio de la preparación y tareas completadas por persona.

En **CMD** desde `flowable-cursos`, con varias negociaciones concluidas, exportá los hitos históricos. Adaptá `-U` y `-d` si cambiaste el usuario o la base del motor.

```bat
docker compose exec -T database psql -X -q -v ON_ERROR_STOP=1 -U flowable -d flowable < ..\proyectoGestion2026\mineria\exportar-negociacion.sql > negociacion.csv
py -m venv .venv
.venv\Scripts\python -m pip install pm4py pandas
where dot
.venv\Scripts\python ..\proyectoGestion2026\mineria\analizar.py negociacion.csv --imagen negociacion.png
```

El CSV tiene instancia, orden, actividad y fecha registrada. El gráfico muestra frecuencias de sucesión entre hitos, y la consola muestra cantidad y porcentaje de hasta tres recorridos. Si `where dot` no devuelve una ruta, instalá [Graphviz](https://graphviz.org/download/) y abrí de nuevo CMD con `dot` en el PATH. Si el CSV solo tiene encabezados, concluí una aceptación, un rechazo, un cierre por plazo o un caso no viable y repetí la exportación.

Para redactar la comparación, contrastá las secuencias observadas con las ramas del BPMN. Indicá cuáles ocurrieron en la muestra, si se observó el ciclo de revisión y el número de recordatorios en las variantes de espera. El CSV selecciona hitos concluidos y excluye negociaciones abiertas y esperas alternativas nunca tomadas. Una ruta sin casos en la demo puede seguir siendo válida en el modelo. Entregá el CSV real, el PNG o su captura, y la conclusión basada en los conteos.
