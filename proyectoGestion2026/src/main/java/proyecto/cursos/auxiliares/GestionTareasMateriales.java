package proyecto.cursos.auxiliares;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.flowable.engine.ProcessEngine;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;

public class GestionTareasMateriales {
    static final class Fila {
        private final String id, instancia, nombre; private final boolean asignada;
        Fila(Task t, String usuario) { id=t.getId(); instancia=t.getProcessInstanceId(); nombre=t.getName(); asignada=usuario.equals(t.getAssignee()); }
        String id(){return id;} String instancia(){return instancia;} String nombre(){return nombre;} boolean asignada(){return asignada;}
    }
    private final ProcessEngine motor;
    public GestionTareasMateriales(ProcessEngine motor) { this.motor = motor; }
    public void identificar(String usuario, String clave) {
        if (!motor.getIdentityService().checkPassword(usuario, clave) || !esAdmin(usuario)) throw new IllegalArgumentException("Credenciales inválidas o usuario ajeno a Administración");
    }
    List<Fila> consultar(String usuario) {
        exigirAdmin(usuario);
        return Stream.concat(materiales().taskAssignee(usuario).list().stream(), disponibles().list().stream())
                .sorted(Comparator.comparing(Task::getCreateTime).thenComparing(Task::getId))
                .map(t -> new Fila(t, usuario)).collect(Collectors.toList());
    }
    public void reclamar(String usuario, String id) {
        org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(usuario);
        try {
            motor.getManagementService().executeCommand(ctx -> {
                exigirAdmin(usuario);
                if (disponibles().taskId(id).count() != 1) throw new IllegalStateException("La tarea ya no está disponible");
                motor.getTaskService().claim(id, usuario);
                return null;
            });
        } finally {
            org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(null);
        }
    }
    public void completar(String usuario, String id) {
        exigirAdmin(usuario);
        if (materiales().taskId(id).taskAssignee(usuario).count() != 1) throw new IllegalStateException("Reclamar la tarea antes de completar");
        org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(usuario);
        try { motor.getTaskService().complete(id); }
        finally { org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(null); }
    }
    private TaskQuery materiales(){return motor.getTaskService().createTaskQuery().processDefinitionKey("Process_Curso").taskDefinitionKey("Material").active();}
    private TaskQuery disponibles(){return materiales().taskUnassigned().taskCandidateGroup("administracion");}
    private boolean esAdmin(String usuario){return motor.getIdentityService().createUserQuery().userId(usuario).memberOfGroup("administracion").count()==1;}
    private void exigirAdmin(String usuario){if(!esAdmin(usuario))throw new IllegalArgumentException("Usuario ajeno a Administración");}
}
