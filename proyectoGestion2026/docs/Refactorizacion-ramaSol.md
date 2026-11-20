# Refactorización de clases en ramaSol

## Clases renombradas

Las rutas parten de `proyectoGestion2026/src/main/java/proyecto/cursos/`. El archivo y la clase pública se renombraron juntos. Los comandos de Maven de esta rama utilizan los nombres nuevos.

| Antes | Archivo actual | Responsabilidad |
|---|---|---|
| `AnalisisHistorico.java` | [auxiliares/InformeHistoricoCursos.java](../src/main/java/proyecto/cursos/auxiliares/InformeHistoricoCursos.java) | Lee el historial de Flowable y presenta resultados, duraciones y tareas completadas por persona. |
| `AppMateriales.java` | [auxiliares/VentanaMaterialesCurso.java](../src/main/java/proyecto/cursos/auxiliares/VentanaMaterialesCurso.java) | Presenta la interfaz Swing de preparación de materiales. |
| `ConexionMotor.java` | [auxiliares/ConexionFlowable.java](../src/main/java/proyecto/cursos/auxiliares/ConexionFlowable.java) | Abre el motor Java que comparten materiales y el informe histórico. |
| `ServicioMateriales.java` | [auxiliares/GestionTareasMateriales.java](../src/main/java/proyecto/cursos/auxiliares/GestionTareasMateriales.java) | Consulta, reclama y completa tareas de materiales con la API Java de Flowable. |
| `Institucion.java` | [RepositorioInstitucional.java](../src/main/java/proyecto/cursos/RepositorioInstitucional.java) | Consulta los cursos, las cuentas y los documentos de la base institucional. |
| `MotorRest.java` | [ClienteRestFlowable.java](../src/main/java/proyecto/cursos/ClienteRestFlowable.java) | Centraliza las solicitudes HTTP a Flowable y el uso de sesiones del responsable. |
| `PermisosRest.java` | [CamposPermitidosTarea.java](../src/main/java/proyecto/cursos/CamposPermitidosTarea.java) | Restringe qué variables puede enviar cada formulario. |
| `PortalCursos.java` | [ControladorPortalCursos.java](../src/main/java/proyecto/cursos/ControladorPortalCursos.java) | Recibe las solicitudes web y coordina tareas, seguimiento y documentos. |
| `SeguridadCursos.java` | [AutenticacionPortalCursos.java](../src/main/java/proyecto/cursos/AutenticacionPortalCursos.java) | Valida las cuentas del portal y determina su rol. |

### Invocaciones y documentación actualizadas

- [CuentaEmpresa.java](../src/main/java/proyecto/cursos/CuentaEmpresa.java) instancia el repositorio institucional.
- [servidor_Institucional.java](../src/main/java/proyecto/cursos/servidor_Institucional.java) importa el controlador nuevo, configura el cliente REST y el repositorio, y conecta la autenticación a la cadena de seguridad.
- [AppComunicaciones.java](../src/main/java/proyecto/cursos/auxiliares/AppComunicaciones.java) y [SimularComunicaciones.java](../src/main/java/proyecto/cursos/auxiliares/SimularComunicaciones.java) usan el nuevo nombre del cliente REST.
- [Predefinitiva.md](Predefinitiva.md) y [Version2.md](Version2.md) usan los nuevos nombres y comandos. La columna **Adjunto** de `Version2.md` identifica los archivos originales que sirvieron de referencia.

## Archivos de la lista ya integrados

| Adjunto original | Dónde vive su funcionalidad |
|---|---|
| `ActualizarEstadoCursoListener.java` | [EstadoCurso.java](../src/main/java/proyecto/cursos/EstadoCurso.java) registra el resultado por instancia. |
| `AsignarAdministradorListener.java` | [TareasCurso.java](../src/main/java/proyecto/cursos/TareasCurso.java) asigna según la carga pendiente. |
| `DatosCursoListener.java` | `TareasCurso` valida las tareas y `EstadoCurso` registra los datos que se proyectan a la base institucional. |
| `EvaluarViabilidadListener.java` e `IniciarCursoListener.java` | [OperacionesCurso.java](../src/main/java/proyecto/cursos/OperacionesCurso.java) calcula el dato de entrada del DMN e inicializa el plazo. |
| `HabilitarEmpresaDelegate.java`, `LiberarAulaDelegate.java` y `ReservarAulaDelegate.java` | `OperacionesCurso` implementa la habilitación, la reserva y la compensación con los IDs del BPMN ejecutable. |
| `ProbarListenersMain.java` y `ProbarDelegatesMain.java` | [OrganizacionCursosTest.java](../src/test/java/proyecto/cursos/OrganizacionCursosTest.java) reúne escenarios equivalentes en JUnit. |

### Decisiones sobre la estructura

- Los listeners y delegados del ejecutable ya concentran las funciones de los adjuntos. Conservan sus nombres actuales porque Flowable guarda referencias a esas clases en las definiciones desplegadas.
- La ventana de materiales, su servicio y la conexión se mantienen separados. El informe histórico también usa la conexión y puede ejecutarse sin abrir la interfaz Swing.
- El controlador del portal coordina peticiones. El cliente HTTP, las reglas de los formularios, la autenticación y el repositorio cumplen funciones separadas. Esta revisión conserva esas responsabilidades y el flujo de finalización de propuestas.
- `servidor_Institucional.java` está en uso. [spring.factories](../src/main/resources/META-INF/spring.factories) lo registra al arrancar Flowable. La configuración incorpora el portal, su cadena de seguridad y los componentes que necesita.

### Archivos candidatos a limpieza

- [ProbarFlowable.java](../src/main/java/proyecto/cursos/ProbarFlowable.java) es una comprobación manual de disponibilidad de una clase. No encontré invocaciones desde el proyecto ni desde las guías actuales.
- [flowable.cfg_167785612063.xml](../src/test/resources/flowable.cfg_167785612063.xml) es una configuración de ejemplo. La suite actual configura su motor H2 desde Java y no encontré una referencia a ese archivo.
- [Miprimerproceso.bpmn20.xml](../src/main/resources/processes/Miprimerproceso.bpmn20.xml) sí se carga en una prueba. Si se limpia la carpeta de procesos de producción, conviene moverlo a los recursos de prueba y conservar ese caso.

## Configuración del portal y referencias de Flowable

- `ServidorCursos68.java` pasó a llamarse `servidor_Institucional.java`. El nuevo nombre se usa en la declaración de clase, en el import de su configuración de seguridad y en `META-INF/spring.factories`.
- Spring carga esta configuración al levantar Flowable. Los comandos `exec:java` siguen apuntando a los programas auxiliares y al instalador, que tienen sus propios métodos `main`.
- El BPMN ejecutable referencia directamente a `EstadoCurso`, `OperacionesCurso` y `TareasCurso`. El renombrado conserva esas referencias y las rutas REST. Los formularios, el modelo BPMN y el DMN pueden conservarse al reconstruir la aplicación.

## Comandos actualizados

Desde `proyectoGestion2026`, después de preparar las variables de entorno indicadas en [Predefinitiva.md](Predefinitiva.md):

```bat
mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.VentanaMaterialesCurso
mvn compile exec:java -Dexec.mainClass=proyecto.cursos.auxiliares.InformeHistoricoCursos
```
