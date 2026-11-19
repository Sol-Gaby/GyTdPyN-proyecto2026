package proyecto.cursos;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Set;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableForbiddenException;
import org.flowable.common.engine.impl.identity.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private static final String RESPONSABLE = "cursosResponsable";
    private static final String SIN_RESPONSABLE = "";
    private static final Set<String> TAREAS_ADMINISTRACION = Set.of(
            "Material", "Activity_12i2hpf", "Virtual", "Equipo", "Activity_1m08pcl");

    @Override
    public void notify(DelegateTask task) {
        ProcessEngineConfigurationImpl motor = CommandContextUtil.getProcessEngineConfiguration();
        if (EVENTNAME_CREATE.equals(task.getEventName())) {
            if ("Lista".equals(task.getTaskDefinitionKey())) {
                asignarLista(task, motor);
            }
            task.setVariableLocal(RESPONSABLE, task.getAssignee() == null ? SIN_RESPONSABLE : task.getAssignee());
            return;
        }
        if (EVENTNAME_ASSIGNMENT.equals(task.getEventName())) {
            validarAsignacion(task, motor);
            return;
        }
        if (task.getAssignee() == null) {
            throw new FlowableForbiddenException("La tarea debe reclamarse antes de completarla");
        }
        if (!task.getAssignee().equals(usuarioActual())) {
            throw new FlowableForbiddenException("Solo el responsable puede completar la tarea");
        }
        if (TAREAS_ADMINISTRACION.contains(task.getTaskDefinitionKey()) && !esAdministrativo(motor, task.getAssignee())) {
            throw new FlowableForbiddenException("La persona asignada debe pertenecer a Administración");
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

    private void validarAsignacion(DelegateTask tarea, ProcessEngineConfigurationImpl motor) {
        Object anterior = tarea.getVariableLocal(RESPONSABLE);
        if (anterior == null) {
            return; // Asignación inicial del BPMN o del listener que crea la lista.
        }
        String actual = tarea.getAssignee();
        if (!TAREAS_ADMINISTRACION.contains(tarea.getTaskDefinitionKey())) {
            if (!anterior.equals(actual)) {
                throw new FlowableForbiddenException("La tarea está asignada directamente y no admite reclamo ni reasignación");
            }
            return;
        }
        String usuario = usuarioActual();
        if (actual == null && anterior.equals(usuario) && usuario != null) {
            tarea.setVariableLocal(RESPONSABLE, SIN_RESPONSABLE);
            return;
        }
        if (!SIN_RESPONSABLE.equals(anterior) || actual == null || !actual.equals(usuario)
                || !esAdministrativo(motor, actual)) {
            throw new FlowableForbiddenException("Solo un integrante de Administración puede reclamar para sí una tarea sin asignar");
        }
        tarea.setVariableLocal(RESPONSABLE, actual);
    }

    private boolean esAdministrativo(ProcessEngineConfigurationImpl motor, String usuario) {
        return motor.getIdentityService().createUserQuery().memberOfGroup(ADMINISTRACION).userId(usuario).count() == 1;
    }

    private String usuarioActual() {
        var autenticado = SecurityContextHolder.getContext().getAuthentication();
        if (autenticado != null && autenticado.isAuthenticated() && !(autenticado instanceof AnonymousAuthenticationToken)) {
            return autenticado.getName();
        }
        return Authentication.getAuthenticatedUserId();
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
