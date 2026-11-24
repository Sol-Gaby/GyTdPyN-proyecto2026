package proyecto.cursos;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.dmn.engine.configurator.DmnEngineConfigurator;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.impl.util.EngineServiceUtil;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import proyecto.cursos.auxiliares.GestionTareasMateriales;

class OrganizacionCursosTest {
    private static final String CLAVE = "Process_Curso";
    private static final Instant INICIO = Instant.parse("2026-11-18T12:00:00Z");
    private static final String FECHA_ACORDADA = "2026-11-18T11:00:00-03:00";
    private static final Map<String, Object> PROPUESTA = Map.of(
            "programa", "Fundamentos de BPMN", "presupuesto", 1000, "fechasPosibles", "18 de noviembre");
    private static ProcessEngine motor;
    private static ProcessEngineConfigurationImpl configuracion;
    private static DmnEngineConfigurator dmn;

    @BeforeAll
    static void iniciarMotor() throws Exception {
        configuracion = (ProcessEngineConfigurationImpl)
                ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        configuracion.setJdbcUrl("jdbc:h2:mem:curso_punto1;DB_CLOSE_DELAY=-1");
        configuracion.setAsyncExecutorActivate(false);
        dmn = new DmnEngineConfigurator();
        configuracion.addConfigurator(dmn);
        motor = configuracion.buildProcessEngine();
        Path esquema = Path.of("..", "flowable-cursos", "sql", "institucion.sql");
        if (!Files.isRegularFile(esquema)) {
            esquema = Path.of("flowable-cursos", "sql", "institucion.sql");
        }
        String archivo = Files.readString(esquema);
        String cola = archivo.substring(archivo.indexOf("BEGIN;") + "BEGIN;".length(), archivo.indexOf("COMMIT;"));
        try (Connection conexion = configuracion.getDataSource().getConnection();
                var ddl = conexion.createStatement()) {
            ddl.execute(cola);
        }
        InstalarCursos.crearPersonal(EngineServiceUtil.getIdmIdentityService(configuracion), "clave-didactica");
        InstalarCursos.crearPersonal(EngineServiceUtil.getIdmIdentityService(configuracion), "clave-didactica");
        InstalarCursos.desplegar(motor, dmn, true);
        InstalarCursos.desplegar(motor, dmn, true);
    }

    @AfterEach
    void limpiarInstancias() {
        motor.getRuntimeService().createProcessInstanceQuery().list().forEach(
                instancia -> motor.getRuntimeService().deleteProcessInstance(instancia.getId(), "fin de prueba"));
    }

    @AfterAll
    static void cerrarMotor() {
        if (motor != null) {
            motor.close();
        }
    }

    @Test
    void solicitudWebReservaUnaReferenciaUnica() throws Exception {
        Map<String, Object> datos = Map.of("solicitudWeb", "{\"cursoNombre\":\"BPMN\"}", "empresaCodigo", "EMP-WEB");
        String referencia = "web-" + java.util.UUID.randomUUID();
        String instancia = motor.getRuntimeService()
                .startProcessInstanceByMessage("Solicitud de curso", referencia, datos).getId();
        assertEquals("EN_TRAMITE", estadoInstitucional(instancia));
        try (Connection conexion = configuracion.getDataSource().getConnection();
                var consulta = conexion.prepareStatement(
                        "SELECT solicitud_referencia, solicitud_web FROM cursos_pendientes WHERE proceso_id = ?")) {
            consulta.setString(1, instancia);
            try (var evento = consulta.executeQuery()) {
                assertTrue(evento.next());
                assertEquals(referencia, evento.getString("solicitud_referencia"));
                assertEquals(datos.get("solicitudWeb"), evento.getString("solicitud_web"));
            }
        }
        assertThrows(FlowableException.class, () -> motor.getRuntimeService()
                .startProcessInstanceByMessage("Solicitud de curso", referencia, datos));
    }

    @Test
    void viabilidadCuentaOtrasInstanciasEnDistintasVersiones() {
        assertEquals(1, motor.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey(CLAVE).latestVersion().count());
        iniciar();
        iniciar();
        InstalarCursos.desplegar(motor, dmn, false);
        iniciar();
        iniciar();
        String quinto = iniciar();
        assertEquals(4L, variable(quinto, "totalCursosVigentes"));
        assertEquals("jefeAdmin", tarea(quinto, "reg_elav_propuesta").getAssignee());
        String sexto = iniciar();
        assertFalse(activa(sexto));
        assertEquals("NO_VIABLE", historica(sexto, "estadoFinal"));
        assertEquals("NO_VIABLE", estadoInstitucional(sexto));
        assertEquals(5L, historica(sexto, "totalCursosVigentes"));
    }

    @Test
    void cambiosReinicianRecordatoriosYRechazoPideArchivo() {
        String id = negociar();
        Job primerPlazo = temporizador(id);
        vencer(primerPlazo);
        assertEquals(1, variable(id, "intentosRecordatorio"));
        assertThrows(FlowableException.class, () -> mensaje(id, "Solicitud de modificaciones", Map.of()));
        mensaje(id, "Solicitud de modificaciones", Map.of("observaciones", "Agregar prácticas"));
        completar(id, "revisar_propuesta", PROPUESTA);
        assertEquals(2, variable(id, "versionPropuesta"));
        assertEquals(0, variable(id, "intentosRecordatorio"));
        assertThrows(FlowableException.class, () -> mensaje(id, "Aceptación", Map.of("fechaCurso", "pronto")));
        mensaje(id, "Rechazo", Map.of());
        assertEquals("jefeAdmin", tarea(id, "archivar_solicitud").getAssignee());
        completar(id, "archivar_solicitud", Map.of());
        assertEquals("RECHAZADO", historica(id, "estadoFinal"));
        assertEquals("RECHAZADO", estadoInstitucional(id));
    }

    @Test
    void tercerVencimientoArchivaSinEnviarUnTercerRecordatorio() {
        String id = negociar();
        assertEquals(INICIO.plusSeconds(5 * 24 * 3600), temporizador(id).getDuedate().toInstant());
        vencer(temporizador(id));
        vencer(temporizador(id));
        assertNotNull(espera(id, "Aceptación"));
        vencer(temporizador(id));
        assertNull(espera(id, "Aceptación"));
        assertEquals(2, variable(id, "intentosRecordatorio"));
        completar(id, "archivar_solicitud", Map.of());
        assertEquals("SIN_RESPUESTA", historica(id, "estadoFinal"));
        assertEquals("SIN_RESPUESTA", estadoInstitucional(id));
    }

    @Test
    void preparacionEsParalelaYFinalNormalEsperaLaCompensacion() {
        String id = preparar();
        assertEquals(false, variable(id, "preparacionCancelada"));
        assertEquals(Set.of("Materiales", "evaluar_recursos"), clavesTareas(id));
        assertNotNull(espera(id, "Nómina de participantes"));
        assertThrows(FlowableException.class, () -> completar(id, "Materiales", Map.of()));
        Task materiales = tarea(id, "Materiales");
        assertThrows(FlowableException.class, () -> como("docente1",
                () -> motor.getTaskService().claim(materiales.getId(), "docente1")));
        assertNull(tarea(id, "Materiales").getAssignee());
        assertThrows(FlowableException.class, () -> como("docente1",
                () -> motor.getTaskService().claim(materiales.getId(), "jefeAdmin")));
        var auxiliarMateriales = new GestionTareasMateriales(motor);
        auxiliarMateriales.reclamar("jefeAdmin", materiales.getId());
        auxiliarMateriales.completar("jefeAdmin", materiales.getId());
        reclamar(id, "evaluar_recursos");
        assertThrows(FlowableException.class, () -> completar(id, "evaluar_recursos", Map.of()));
        completar(id, "evaluar_recursos", recursos(true, true, true));
        assertEquals(Set.of("Virtual", "contrato_equipo"), clavesTareas(id));
        reclamarYCompletar(id, "Virtual", Map.of());
        assertNull(tareaOpcional(id, "definir_docente_resp"));
        mensaje(id, "Nómina de participantes", Map.of("nomina", "[\"Ana\",\"Pablo\"]"));
        assertEquals("adminCursos", tarea(id, "confeccion_lista").getAssignee());
        completar(id, "confeccion_lista", Map.of());
        assertNull(tareaOpcional(id, "definir_docente_resp"));
        reclamarYCompletar(id, "contrato_equipo", Map.of());
        assertNull(espera(id, "Cancelación"), () -> estado(id));

        assertEquals("jefeDocente", tarea(id, "definir_docente_resp").getAssignee());
        assertNull(tareaOpcional(id, "verificar_compensaciones"));
        assertEquals("Curso confirmado", variable(id, "ultimoAvisoEmpresa"));
        assertThrows(FlowableException.class,
                () -> completar(id, "definir_docente_resp", Map.of("docenteAsignado", "jefeAdmin")));
        completar(id, "definir_docente_resp", Map.of("docenteAsignado", "jefeDocente"));
        assertNull(tareaOpcional(id, "dictar_curso"));
        assertEquals(Instant.parse("2026-11-18T14:00:00Z"), temporizador(id).getDuedate().toInstant());
        vencer(temporizador(id));
        assertEquals("jefeDocente", tarea(id, "dictar_curso").getAssignee());
        completar(id, "dictar_curso", Map.of());
        completar(id, "Informe", Map.of("informeResultados", "informe.pdf"));
        completar(id, "envio_Informe", Map.of());
        assertTrue(activa(id));
        assertEquals("EN_TRAMITE", estadoInstitucional(id));
        assertNotNull(tarea(id, "liberar_equipo"));
        reclamarYCompletar(id, "liberar_equipo", Map.of());
        assertFalse(activa(id), () -> estado(id));
        assertEquals("FINALIZADO", historica(id, "estadoFinal"));
        assertEquals("FINALIZADO", estadoInstitucional(id));
        assertEquals(true, historica(id, "aulaLiberada"));
        assertEquals(true, historica(id, "aulaVirtualLiberada"));
        assertEquals(true, historica(id, "equipamientoLiberado"));
    }

    @Test
    void cancelacionInterrumpeYEsperaLiberarSoloRecursosGestionados() {
        String id = preparar();
        reclamarYCompletar(id, "evaluar_recursos", recursos(true, true, true));
        reclamarYCompletar(id, "contrato_equipo", Map.of());
        mensaje(id, "Cancelación", Map.of());
        assertNull(espera(id, "Nómina de participantes"));
        assertEquals(Set.of("liberar_equipo", "verificar_compensaciones"), clavesTareas(id));
        assertEquals(true, variable(id, "aulaLiberada"));
        assertNull(variable(id, "aulaVirtualLiberada"));
        assertEquals(false, variable(id, "preparacionCancelada"));
        assertThrows(FlowableException.class, () -> completar(id, "verificar_compensaciones", Map.of()));
        assertTrue(activa(id));
        assertNotEquals("Cancelación comunicada a la empresa", variable(id, "ultimoAvisoEmpresa"));

        reclamarYCompletar(id, "liberar_equipo", Map.of());
        assertEquals(Set.of("verificar_compensaciones"), clavesTareas(id));
        assertEquals("EN_TRAMITE", estadoInstitucional(id));
        assertEquals(false, variable(id, "preparacionCancelada"));
        assertTrue(activa(id));
        Task pausa = tarea(id, "verificar_compensaciones");
        assertEquals("jefeAdmin", pausa.getAssignee());
        assertThrows(FlowableException.class,
                () -> como("adminCursos", () -> motor.getTaskService().complete(pausa.getId())));
        completar(id, "verificar_compensaciones", Map.of());

        assertFalse(activa(id), () -> estado(id));
        assertEquals(true, historica(id, "preparacionCancelada"));
        assertEquals("Cancelación comunicada a la empresa", historica(id, "ultimoAvisoEmpresa"));
        assertEquals("CANCELADO", historica(id, "estadoFinal"));
        assertEquals("CANCELADO", estadoInstitucional(id));
        assertEquals(true, historica(id, "aulaLiberada"));
        assertEquals(true, historica(id, "equipamientoLiberado"));
        assertNull(historica(id, "aulaVirtualLiberada"));
    }

    @Test
    void cancelacionCompensaElAulaVirtualGestionadaSinActivarOtrasLiberaciones() {
        String id = preparar();
        reclamarYCompletar(id, "evaluar_recursos", recursos(false, true, false));
        reclamarYCompletar(id, "Virtual", Map.of());
        assertEquals(false, variable(id, "aulaVirtualLiberada"));

        mensaje(id, "Cancelación", Map.of());
        assertEquals(Set.of("verificar_compensaciones"), clavesTareas(id));
        assertEquals(true, variable(id, "aulaVirtualLiberada"));
        assertNull(variable(id, "aulaLiberada"));
        completar(id, "verificar_compensaciones", Map.of());
        assertEquals("CANCELADO", historica(id, "estadoFinal"));
    }

    @Test
    void cancelacionSinRecursosGestionadosNoCreaTareasDeLiberacion() {
        String id = preparar();
        reclamarYCompletar(id, "evaluar_recursos", recursos(false, false, true));
        assertEquals(Set.of("Materiales", "contrato_equipo"), clavesTareas(id));
        mensaje(id, "Cancelación", Map.of());
        assertEquals(Set.of("verificar_compensaciones"), clavesTareas(id));
        assertNull(variable(id, "aulaLiberada"));
        assertNull(variable(id, "aulaVirtualLiberada"));
        completar(id, "verificar_compensaciones", Map.of());
        assertFalse(activa(id), () -> estado(id));
        assertEquals("CANCELADO", historica(id, "estadoFinal"));
        assertEquals("CANCELADO", estadoInstitucional(id));
        assertEquals(false, historica(id, "equipamientoLiberado"));
    }

    @Test
    void faltaDeAulaEsReintentable() {
        String id = preparar();
        reclamar(id, "evaluar_recursos");
        FlowableException error = assertThrows(FlowableException.class, () -> completar(id, "evaluar_recursos",
                Map.of("requiereAula", true, "aulaDisponible", false)));
        assertTrue(error.getMessage().contains("AULA_NO_DISPONIBLE"));
        assertNotNull(tarea(id, "evaluar_recursos"));
        assertNull(variable(id, "idReservaAula"));
        completar(id, "evaluar_recursos", recursos(false, true, false));
        assertEquals(Set.of("Materiales", "Virtual"), clavesTareas(id));
    }

    private String iniciar() {
        configuracion.getClock().setCurrentTime(Date.from(INICIO));
        return motor.getRuntimeService().startProcessInstanceByMessage("Solicitud de curso").getId();
    }

    private String negociar() {
        String id = iniciar();
        completar(id, "reg_elav_propuesta", PROPUESTA);
        return id;
    }

    private String preparar() {
        String id = negociar();
        mensaje(id, "Aceptación", Map.of("fechaCurso", FECHA_ACORDADA));
        return id;
    }

    private static Map<String, Object> recursos(boolean aula, boolean virtual, boolean equipo) {
        return Map.of("requiereAula", aula, "requierePlataforma", virtual, "requiereEquipamiento", equipo);
    }

    private Execution espera(String id, String nombre) {
        return motor.getRuntimeService().createExecutionQuery().processInstanceId(id)
                .messageEventSubscriptionName(nombre).singleResult();
    }

    private void mensaje(String id, String nombre, Map<String, Object> datos) {
        Execution ejecucion = espera(id, nombre);
        assertNotNull(ejecucion, "Debe estar esperando " + nombre);
        motor.getRuntimeService().messageEventReceived(nombre, ejecucion.getId(), datos);
    }

    private void reclamarYCompletar(String id, String actividad, Map<String, Object> variables) {
        reclamar(id, actividad);
        completar(id, actividad, variables);
    }

    private void reclamar(String id, String actividad) {
        como("jefeAdmin", () -> motor.getTaskService().claim(tarea(id, actividad).getId(), "jefeAdmin"));
    }

    private void completar(String id, String actividad, Map<String, Object> variables) {
        Task pendiente = tarea(id, actividad);
        como(pendiente.getAssignee(), () -> motor.getTaskService().complete(pendiente.getId(), variables));
    }

    private void como(String usuario, Runnable operacion) {
        Authentication.setAuthenticatedUserId(usuario);
        try {
            operacion.run();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    @Test
    void soloElResponsableCompletaYUnaTareaDirectaNoSeReclama() {
        String id = iniciar();
        String tarea = tarea(id, "reg_elav_propuesta").getId();
        assertThrows(FlowableException.class, () -> como("adminCursos",
                () -> motor.getTaskService().complete(tarea, PROPUESTA)));
        assertThrows(FlowableException.class, () -> como("jefeAdmin",
                () -> motor.getTaskService().unclaim(tarea)));
        assertThrows(FlowableException.class, () -> como("adminCursos",
                () -> motor.getTaskService().setAssignee(tarea, "adminCursos")));
        assertEquals("jefeAdmin", tarea(id, "reg_elav_propuesta").getAssignee());
        completar(id, "reg_elav_propuesta", PROPUESTA);
        assertNull(tareaOpcional(id, "reg_elav_propuesta"));
        assertNotNull(espera(id, "Aceptación"));
        assertEquals("EN_TRAMITE", estadoInstitucional(id));
    }

    @Test
    void completarPropuestaEncolaSuDocumentoConLosDatosCargados() throws SQLException {
        String id = iniciar();
        completar(id, "reg_elav_propuesta", PROPUESTA);

        String sql = "SELECT programa, presupuesto, fechas_posibles FROM cursos_pendientes "
                + "WHERE proceso_id = ? AND documento_tipo = 'PROPUESTA'";
        try (Connection conexion = configuracion.getDataSource().getConnection();
                PreparedStatement consulta = conexion.prepareStatement(sql)) {
            consulta.setString(1, id);
            try (ResultSet fila = consulta.executeQuery()) {
                assertTrue(fila.next());
                assertEquals(PROPUESTA.get("programa"), fila.getString("programa"));
                assertEquals(0, fila.getBigDecimal("presupuesto").compareTo(java.math.BigDecimal.valueOf(1000)));
                assertEquals(PROPUESTA.get("fechasPosibles"), fila.getString("fechas_posibles"));
                assertFalse(fila.next());
            }
        }
    }

    @Test
    void listaDefinitivaSeAsignaAlAdministrativoConMenosPendientes() {
        Task carga = motor.getTaskService().newTask();
        carga.setName("Tarea asignada previamente");
        carga.setAssignee("adminCursos");
        motor.getTaskService().saveTask(carga);
        try {
            String id = preparar();
            mensaje(id, "Nómina de participantes", Map.of("nomina", "[\"Ana\"]"));
            assertEquals("jefeAdmin", tarea(id, "confeccion_lista").getAssignee());
        } finally {
            motor.getTaskService().deleteTask(carga.getId());
        }
    }

    private String estadoInstitucional(String id) {
        String sql = "SELECT estado FROM cursos_pendientes WHERE proceso_id = ? ORDER BY revision DESC LIMIT 1";
        try (Connection conexion = configuracion.getDataSource().getConnection();
                PreparedStatement consulta = conexion.prepareStatement(sql)) {
            consulta.setString(1, id);
            try (ResultSet resultado = consulta.executeQuery()) {
                return resultado.next() ? resultado.getString(1) : null;
            }
        } catch (SQLException ex) {
            throw new AssertionError(ex);
        }
    }

    private Task tarea(String id, String actividad) {
        Task resultado = tareaOpcional(id, actividad);
        assertNotNull(resultado, "Debe existir la tarea " + actividad + ". Activas: " + clavesTareas(id));
        return resultado;
    }

    private Task tareaOpcional(String id, String actividad) {
        return motor.getTaskService().createTaskQuery().processInstanceId(id).taskDefinitionKey(actividad).singleResult();
    }

    private Set<String> clavesTareas(String id) {
        return motor.getTaskService().createTaskQuery().processInstanceId(id).list()
                .stream().map(Task::getTaskDefinitionKey).collect(Collectors.toSet());
    }

    private Job temporizador(String id) {
        Job job = motor.getManagementService().createTimerJobQuery().processInstanceId(id).singleResult();
        assertNotNull(job);
        return job;
    }

    private void vencer(Job job) {
        configuracion.getClock().setCurrentTime(Date.from(job.getDuedate().toInstant().plusMillis(1)));
        Job ejecutable = motor.getManagementService().moveTimerToExecutableJob(job.getId());
        motor.getManagementService().executeJob(ejecutable.getId());
    }

    private String estado(String id) {
        return "Tareas: " + clavesTareas(id) + ". Ejecuciones: "
                + motor.getRuntimeService().createExecutionQuery().processInstanceId(id).list();
    }

    private boolean activa(String id) {
        return motor.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).count() == 1;
    }

    private Object variable(String id, String nombre) {
        return motor.getRuntimeService().getVariable(id, nombre);
    }

    private Object historica(String id, String nombre) {
        var variable = motor.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(id)
                .variableName(nombre).singleResult();
        return variable == null ? null : variable.getValue();
    }
}
