package proyecto.cursos;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Set;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;

public class TareasCurso implements TaskListener {
    private static final long serialVersionUID = 1L;
    public static final String ADMINISTRACION = "administracion";
    public static final String DOCENTES = "docentes";
    private static final Set<String> TAREAS_ADMINISTRACION = Set.of(
            "Material", "Activity_12i2hpf", "Virtual", "Equipo", "Activity_1m08pcl");

    @Override
    public void notify(DelegateTask task) {
        ProcessEngineConfigurationImpl motor = CommandContextUtil.getProcessEngineConfiguration();
        if (EVENTNAME_CREATE.equals(task.getEventName())) {
            asignarLista(task, motor);
            return;
        }
        if (task.getAssignee() == null) {
            throw new FlowableException("La tarea debe reclamarse antes de completarla");
        }
        if (TAREAS_ADMINISTRACION.contains(task.getTaskDefinitionKey())
                && motor.getIdentityService().createUserQuery().memberOfGroup(ADMINISTRACION)
                        .userId(task.getAssignee()).count() != 1) {
            throw new FlowableException("La persona asignada debe pertenecer a Administración");
        }
        switch (task.getTaskDefinitionKey()) {
            case "Propuesta":
            case "Revisar":
                exigirTexto(task, "programa");
                exigirTexto(task, "fechasPosibles");
                try {
                    if (new BigDecimal(String.valueOf(task.getVariable("presupuesto"))).signum() < 0) {
                        throw new NumberFormatException("presupuesto negativo");
                    }
                } catch (NumberFormatException ex) {
                    throw new FlowableException("El presupuesto debe ser un número mayor o igual a cero", ex);
                }
                break;
            case "Activity_12i2hpf":
                boolean aula = opcion(task, "requiereAula");
                boolean virtual = opcion(task, "requierePlataforma");
                boolean equipo = opcion(task, "requiereEquipamiento");
                if (!aula && !virtual && !equipo) {
                    throw new FlowableException("Seleccioná al menos un recurso");
                }
                break;
            case "Activity_1018bdz":
                String docente = exigirTexto(task, "docenteAsignado");
                if (motor.getIdentityService().createUserQuery().memberOfGroup(DOCENTES).userId(docente).count() != 1) {
                    throw new FlowableException("El docente seleccionado no pertenece al grupo docentes");
                }
                break;
            case "Informe":
                exigirTexto(task, "informeResultados");
                break;
            case "Equipo":
                task.setVariable("equipamientoGestionado", true);
                break;
            case "Activity_1m08pcl":
                task.setVariable("equipamientoLiberado", true);
                reanudarCancelacion(task, motor.getRuntimeService());
                break;
            default:
                break;
        }
    }

    private void reanudarCancelacion(DelegateTask tarea, RuntimeService runtime) {
        String mensaje = "Liberación de equipamiento completada";
        Execution espera = runtime.createExecutionQuery().processInstanceId(tarea.getProcessInstanceId())
                .messageEventSubscriptionName(mensaje).singleResult();
        if (espera != null) {
            runtime.messageEventReceived(mensaje, espera.getId());
        }
    }

    private void asignarLista(DelegateTask task, ProcessEngineConfigurationImpl motor) {
        String responsable = motor.getIdentityService().createUserQuery().memberOfGroup(ADMINISTRACION)
                .orderByUserId().asc().list().stream().map(User::getId)
                .min(Comparator.comparingLong((String usuario) -> motor.getTaskService().createTaskQuery()
                        .taskAssignee(usuario).count()).thenComparing(Comparator.naturalOrder()))
                .orElseThrow(() -> new FlowableException("El grupo administracion no tiene integrantes"));
        motor.getTaskService().setAssignee(task.getId(), responsable);
    }

    private boolean opcion(DelegateTask task, String variable) {
        Object valor = task.getVariable(variable);
        if (valor != null && !(valor instanceof Boolean)) {
            throw new FlowableException(variable + " debe ser true o false");
        }
        boolean elegido = Boolean.TRUE.equals(valor);
        task.setVariable(variable, elegido);
        return elegido;
    }

    private String exigirTexto(DelegateTask task, String variable) {
        Object valor = task.getVariable(variable);
        if (!(valor instanceof String) || ((String) valor).isBlank()) {
            throw new FlowableException("Falta la variable " + variable);
        }
        return (String) valor;
    }
}
