package proyecto.cursos;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.logging.Logger;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;

public class OperacionesCurso implements JavaDelegate {
    private static final Logger LOG = Logger.getLogger(OperacionesCurso.class.getName());

    @Override
    public void execute(DelegateExecution execution) {
        switch (IdentificadoresCurso.actual(execution.getCurrentActivityId())) {
            case "contar_cursos":
                contarCursos(execution);
                break;
            case "notificar_inviabilidad":
                execution.setVariable("estadoFinal", "NO_VIABLE");
                notificar(execution, "Solicitud no viable");
                break;
            case "enviar_propuesta":
                execution.setVariable("intentosRecordatorio", 0);
                Object version = execution.getVariable("versionPropuesta");
                execution.setVariable("versionPropuesta", version == null ? 1 : ((Number) version).intValue() + 1);
                notificar(execution, "Propuesta enviada");
                break;
            case "envio_recordatorio":
                int enviados = (Integer) execution.getVariable("intentosRecordatorio");
                execution.setVariable("intentosRecordatorio", enviados + 1);
                notificar(execution, "Recordatorio " + (enviados + 1) + " enviado");
                break;
            case "reg_observaciones":
                exigirTexto(execution, "observaciones");
                break;
            case "habilita_proceso_empresa":
                validarFecha(execution);
                execution.setVariable("accesoEmpresaHabilitado", true);
                // Las definiciones anteriores inicializan esta variable al aceptar.
                execution.setVariable("preparacionCancelada", false);
                execution.setVariable("equipamientoGestionado", false);
                execution.setVariable("equipamientoLiberado", false);
                notificar(execution, "Propuesta aceptada. Habilitación de acceso simulada");
                break;
            case "validar_nomina":
                exigirTexto(execution, "nomina");
                break;
            case "Aula":
                if (Boolean.FALSE.equals(execution.getVariable("aulaDisponible"))) {
                    throw new FlowableException("AULA_NO_DISPONIBLE");
                }
                execution.setVariable("idReservaAula", "AULA-" + execution.getProcessInstanceId());
                execution.setVariable("aulaLiberada", false);
                break;
            case "Activity_16zz91p":
                execution.setVariable("aulaLiberada", true);
                break;
            case "Activity_02hrooc":
                execution.setVariable("aulaVirtualLiberada", true);
                break;
            case "notificar_cierre":
                execution.setVariable("estadoFinal", "CANCELADO");
                notificar(execution, "Cancelación comunicada a la empresa");
                break;
            case "ComprobarEquipoLiberado": // Compatibilidad con cancelaciones ya desplegadas.
                if (!Boolean.TRUE.equals(execution.getVariable("equipamientoLiberado"))) {
                    throw new FlowableException("Todavía hay equipamiento pendiente de liberación");
                }
                break;
            case "envio_confirmacion_curso":
                notificar(execution, "Curso confirmado");
                break;
            default:
                throw new IllegalStateException("Actividad automática desconocida: " + execution.getCurrentActivityId());
        }
    }

    private void contarCursos(DelegateExecution execution) {
        ProcessEngineConfigurationImpl motor = CommandContextUtil.getProcessEngineConfiguration();
        if ("postgres".equals(motor.getDatabaseType())) {
            bloquearEvaluacionDeCupo();
        }
        String clave = motor.getRepositoryService().getProcessDefinition(execution.getProcessDefinitionId()).getKey();
        long cantidad = motor.getRuntimeService().createProcessInstanceQuery().processDefinitionKey(clave).list()
                .stream().filter(instancia -> !instancia.getId().equals(execution.getProcessInstanceId())).count();
        execution.setVariable("totalCursosVigentes", cantidad);

        Object plazo = execution.getVariable("plazoRespuesta");
        if (plazo == null) {
            execution.setVariable("plazoRespuesta", "P5D");
        } else if (Duration.parse(plazo.toString()).compareTo(Duration.ZERO) <= 0) {
            throw new FlowableException("plazoRespuesta debe ser una duración positiva");
        }
    }

    private void bloquearEvaluacionDeCupo() {
        // La conexión es la de esta ejecución. PostgreSQL libera el bloqueo al confirmar o revertir.
        try (PreparedStatement bloqueo = CommandContextUtil.getDbSqlSession().getSqlSession().getConnection()
                .prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
            bloqueo.setInt(1, 0x43555253); // Espacio de nombres CURS.
            bloqueo.setInt(2, 1);
            bloqueo.execute();
        } catch (SQLException ex) {
            throw new FlowableException("No se pudo bloquear la evaluación de cupo", ex);
        }
    }

    private void validarFecha(DelegateExecution execution) {
        Object fecha = execution.getVariable("fechaCurso");
        if (fecha instanceof Date) {
            return;
        }
        if (!(fecha instanceof String)) {
            throw new FlowableException("Falta fechaCurso");
        }
        try {
            OffsetDateTime.parse((String) fecha);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new FlowableException("fechaCurso requiere fecha, hora y zona", ex);
        }
    }

    private void exigirTexto(DelegateExecution execution, String variable) {
        Object valor = execution.getVariable(variable);
        if (!(valor instanceof String) || ((String) valor).isBlank()) {
            throw new FlowableException("Falta la variable " + variable);
        }
    }

    private void notificar(DelegateExecution execution, String mensaje) {
        execution.setVariable("ultimoAvisoEmpresa", mensaje);
        LOG.info(() -> execution.getProcessInstanceId() + " | " + mensaje);
    }
}
