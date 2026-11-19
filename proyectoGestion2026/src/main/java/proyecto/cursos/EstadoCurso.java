package proyecto.cursos;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.util.CommandContextUtil;

/** Registra el estado del curso en la misma transacción que su avance en Flowable. */
public class EstadoCurso implements ExecutionListener {
    private static final long serialVersionUID = 1L;
    private static final Set<String> CIERRES = Set.of(
            "FINALIZADO", "CANCELADO", "RECHAZADO", "SIN_RESPUESTA", "NO_VIABLE");

    @Override
    public void notify(DelegateExecution execution) {
        if (EVENTNAME_START.equals(execution.getEventName()) || EVENTNAME_TAKE.equals(execution.getEventName())) {
            registrar(execution, "EN_TRAMITE");
        } else {
            Object resultado = execution.getVariable("resultado");
            if (execution.isEnded() && resultado instanceof String && CIERRES.contains(resultado)) {
                registrar(execution, (String) resultado);
            }
        }
    }

    static void registrar(DelegateExecution execution, String estado) {
        String sql = "INSERT INTO cursos_pendientes (proceso_id, codigo_empresa, nombre_empresa, nombre_curso, "
                + "descripcion, programa, presupuesto, fechas_posibles, fecha_inicio, informe, estado) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = CommandContextUtil.getDbSqlSession().getSqlSession().getConnection()
                .prepareStatement(sql)) {
            String codigo = texto(execution, "empresaCodigo", "DEMO");
            if (codigo.length() > 100) {
                throw new FlowableException("empresaCodigo admite hasta 100 caracteres");
            }
            insert.setString(1, execution.getProcessInstanceId());
            insert.setString(2, codigo);
            insert.setString(3, texto(execution, "empresaNombre", "Empresa de demostración"));
            insert.setString(4, texto(execution, "cursoNombre", "Curso de capacitación"));
            insert.setString(5, texto(execution, "cursoDescripcion", "Solicitud de capacitación"));
            insert.setString(6, texto(execution, "programa", null));
            Object presupuesto = execution.getVariable("presupuesto");
            insert.setBigDecimal(7, presupuesto == null ? null : new BigDecimal(presupuesto.toString()));
            insert.setString(8, texto(execution, "fechasPosibles", null));
            insert.setObject(9, fecha(execution.getVariable("fechaCurso")));
            insert.setString(10, texto(execution, "informeResultados", null));
            insert.setString(11, estado);
            insert.executeUpdate();
        } catch (SQLException ex) {
            throw new FlowableException("No se pudo encolar el estado institucional del curso", ex);
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            throw new FlowableException("No son válidos el presupuesto o la fecha del curso", ex);
        }
    }

    private static OffsetDateTime fecha(Object valor) {
        if (valor instanceof Date) {
            return ((Date) valor).toInstant().atOffset(ZoneOffset.UTC);
        }
        return valor == null ? null : OffsetDateTime.parse(valor.toString());
    }

    private static String texto(DelegateExecution execution, String nombre, String defecto) {
        Object valor = execution.getVariable(nombre);
        return valor == null || valor.toString().isBlank() ? defecto : valor.toString().trim();
    }
}
