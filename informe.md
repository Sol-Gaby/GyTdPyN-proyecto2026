# Informe de Implementación — Organización de Cursos de Capacitación

**Materia:** Gestión y Tecnologías de Procesos de Negocio (2026)  
**Integrantes:** Ludueña Augusto, Adorno Gabriela Soledad  

---

## 1. Decisiones de Arquitectura y Persistencia

Para no mezclar los datos propios de la institución con las tablas internas de Flowable, armamos dos bases de datos en PostgreSQL 15 dentro del mismo contenedor (`flowable` para el motor e `institucion_cursos` para las empresas, cursos y PDFs).

El problema que resolvimos con esta separación es mantener ambas bases sincronizadas sin perder consistencia si algo falla a mitad de una transacción. Para eso usamos una tabla intermedia (`cursos_pendientes`) en la base del motor:

- Cuando el proceso avanza por puntos clave (inicio, envío de propuesta, aceptación, carga de informe o cierre), el listener **`EstadoCurso`** inserta una fila en `cursos_pendientes` aprovechando la misma conexión JDBC y transacción de Flowable.
- Luego, **`ProyeccionInstitucional`** lee esa cola en orden por número de `revision`, actualiza las tablas `empresas` y `cursos` en `institucion_cursos`, y genera los PDF versionados de propuestas e informes usando Apache PDFBox (`DocumentosCurso`).

## 2. Organización de Usuarios y Grupos

Al ejecutar `InstalarCursos`, se crean en el módulo de identidad de Flowable los dos grupos y los cinco integrantes pedidos en el enunciado:

- **Administración (`gestion_academica`):** `jefeAdmin` y `adminCursos`.
- **Docentes (`profesores`):** `jefeDocente`, `docente1` y `docente2`.

Las tareas exclusivas de cada jefatura (`Propuesta`, `Revisar`, `Archivar`, `EnviarInforme`, `VerificarCompensaciones` para `jefeAdmin`; `Definir Docente` e `Informe` para `jefeDocente`) van asignadas directamente por `assignee`. El resto de las tareas operativas de administración salen con `candidateGroups="gestion_academica"` para que cualquiera de los dos administrativos las reclame antes de completarlas.

Para el acceso de las empresas solicitantes al portal web no creamos usuarios dentro de Flowable: una vez aceptada la propuesta, se habilita la empresa en `institucion_cursos` (`acceso_habilitado = true`) y se le asigna su contraseña con `CuentaEmpresa` (guardada con hash BCrypt). El proveedor `AutenticacionPortalCursos` valida usuarios con prefijo `empresa-<CODIGO>` contra esa tabla y al personal interno contra Flowable.

## 3. Regla de Viabilidad (DMN) y Temporizadores

- **Evaluación de viabilidad (`viabilidadCurso`):** Antes de entrar a la *Decision Task*, el delegado `OperacionesCurso` (`ContarCursos`) consulta al `RuntimeService` cuántas instancias activas existen de `Process_Curso` (contando todas las versiones desplegadas y filtrando el ID de la instancia actual). En PostgreSQL tomamos un bloqueo transaccional (`pg_advisory_xact_lock`) durante ese conteo para evitar que dos solicitudes simultáneas superen el cupo de 5 cursos vigentes. La tabla DMN recibe `totalCursosVigentes` y devuelve `cursoViable = true` si es menor a 5, o `false` en caso contrario.
- **Recordatorios y vencimientos (`Plazo`):** En lugar de dejar fijo `P5D` en el XML, el timer lee la variable `${plazoRespuesta}` (que se inicializa en `P5D` si no se pasa otra duración, lo que nos permite acortarla a segundos durante las pruebas). Cada vez que vence sin respuesta, si `intentosRecordatorio < 2` se envía un recordatorio y se suma 1; al tercer vencimiento (`>= 2`) pasa a `Archivar solicitud` con `estadoFinal = SIN_RESPUESTA`. Si la empresa pide modificaciones y se envía una nueva versión de la propuesta, `Enviar` vuelve `intentosRecordatorio` a `0`.
- **Inicio del curso (`Fecha`):** Al recibir el mensaje `Aceptación`, exigimos que la empresa envíe `fechaCurso` en formato ISO-8601 con zona horaria. El temporizador previo a `Desarrollar curso` toma directamente `${fechaCurso}` como `timeDate`.

## 4. Variables del Proceso

Las principales variables que viajan en la instancia de `Process_Curso` son:

- **Datos de solicitud y propuesta:** `empresaCodigo`, `empresaNombre`, `cursoNombre`, `cursoDescripcion`, `solicitudWeb`, `programa`, `presupuesto`, `fechasPosibles`, `versionPropuesta` y `observaciones`.
- **Control de negociación y plazos:** `totalCursosVigentes`, `cursoViable`, `plazoRespuesta`, `intentosRecordatorio` y `fechaCurso`.
- **Preparación y recursos:** `nomina` (lista JSON de participantes), `requiereAula`, `requierePlataforma`, `requiereEquipamiento`, `aulaDisponible`, `idReservaAula` y `preparacionCancelada`.
- **Banderas de compensación:** `aulaLiberada`, `aulaVirtualLiberada`, `equipamientoGestionado` y `equipamientoLiberado`.
- **Dictado y cierre:** `docenteAsignado`, `informeResultados`, `ultimoAvisoEmpresa` y `estadoFinal` (`FINALIZADO`, `CANCELADO`, `RECHAZADO`, `SIN_RESPUESTA`, `NO_VIABLE`).

## 5. Listeners y Delegados Implementados

- **`TareasCurso` (`TaskListener`):**
  - *Balanceo de carga (`create` en `Lista`):* Al crearse *Confeccionar lista definitiva*, cuenta cuántas tareas pendientes tiene asignadas directamente cada miembro de `gestion_academica` (ignorando las tareas sin reclamar del grupo candidato) y se la asigna al que tenga menos.
  - *Control de reclamos (`assignment`):* Bloquea intentos de reclamar o reasignar tareas que ya tienen responsable directo y verifica que quien reclama una tarea de grupo pertenezca realmente a `gestion_academica`.
  - *Validaciones de negocio (`complete`):* Controla que el presupuesto no sea negativo, que en *Evaluar Recursos* se marque al menos una opción, que `docenteAsignado` pertenezca al grupo `profesores` y que no se pueda cerrar una cancelación sin haber liberado los recursos gestionados.
- **`OperacionesCurso` (`JavaDelegate`):** Resuelve las tareas de servicio del diagrama: conteo de cursos, notificaciones simuladas a la empresa, reinicio de recordatorios, validación de observaciones y nómina, simulación del web service de reserva/liberación de aula física (lanzando `AULA_NO_DISPONIBLE` si `aulaDisponible` es falso) y notificaciones de cierre.
- **`EstadoCurso` (`ExecutionListener`):** Escucha el inicio, las transiciones con documentos (`PROPUESTA` e `INFORME`) y el fin de la instancia para registrar en `cursos_pendientes` el estado definitivo del curso.

## 6. Manejo de Cancelaciones y Compensaciones

Cada tarea de gestión de recursos dentro de `Preparar curso` (`Aula`, `Virtual`, `Equipo`) tiene adjunto un *Compensation Boundary Event* conectado a su actividad de liberación (`Liberar Aula`, `Liberar Aula Virtual`, `Liberar Equipamiento`).

- Si la empresa envía el mensaje `Cancelación` durante la preparación, el subproceso de eventos interruptor captura el mensaje y dispara las compensaciones únicamente sobre los recursos que llegaron a ejecutarse. Antes de dar por cerrada la cancelación, `jefeAdmin` confirma en `VerificarCompensaciones` que los recursos gestionados hayan quedado liberados.
- Si el curso termina normalmente, después de `Enviar informe a empresa y Archivar expediente` se ejecuta el evento intermedio `CompensarPreparacion`, que dispara la compensación sobre todo el subproceso `Preparar curso` para liberar los recursos utilizados antes de llegar al evento final.

## 7. Puesta en Marcha y Ejecución

1. **Levantar base y Flowable:** Desde `flowable-cursos/`, configurar `.env` y ejecutar `docker compose up -d --build`. Luego instalar el modelo BPMN, la tabla DMN, los formularios y los usuarios con:
   
   
   docker compose exec -T flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.InstalarCursos