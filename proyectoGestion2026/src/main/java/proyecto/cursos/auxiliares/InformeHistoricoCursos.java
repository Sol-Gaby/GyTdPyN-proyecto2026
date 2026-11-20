package proyecto.cursos.auxiliares;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.history.HistoricActivityInstance;

/** Consultas 4.3 sobre todas las versiones de Process_Curso. */
public final class InformeHistoricoCursos {
    private static final String CLAVE = "Process_Curso";

    private static final class Informe {
        final Map<String, Long> resultados = new LinkedHashMap<>();
        final Map<String, Long> tareas = new TreeMap<>();
        long terminadas;
        long preparaciones;
        long inscripciones;
        long materiales;
        long recursos;

        Informe() {
            for (String resultado : List.of("FINALIZADO", "CANCELADO", "RECHAZADO", "NO_VIABLE",
                    "SIN_RESPUESTA", "SIN_CLASIFICAR")) {
                resultados.put(resultado, 0L);
            }
        }

        void medirPreparacion(List<HistoricActivityInstance> actividades) {
            if (actividades.stream().noneMatch(a -> "SubFin".equals(a.getActivityId()))) return;
            Long inscripcion = intervalo(actividades, "Nomina", "Lista");
            Long material = intervalo(actividades, "Material", "Material");
            Long recurso = intervalo(actividades, "Activity_12i2hpf", "OrFin");
            if (inscripcion == null || material == null || recurso == null) return;
            preparaciones++;
            inscripciones += inscripcion;
            materiales += material;
            recursos += recurso;
        }

        void imprimir() {
            System.out.println("4.3.1 Resultados de " + terminadas + " solicitudes terminadas");
            System.out.printf("%-22s %10s %12s%n", "Resultado", "Cantidad", "Porcentaje");
            resultados.forEach((resultado, cantidad) -> System.out.printf(Locale.ROOT,
                    "%-22s %10d %10.2f %%%n", resultado, cantidad, terminadas == 0 ? 0.0 : 100.0 * cantidad / terminadas));
            System.out.println("\n4.3.2 Preparaciones normales: " + preparaciones);
            System.out.printf("%-22s %20s%n", "Rama", "Duración media");
            imprimirPromedio("Inscripciones", inscripciones);
            imprimirPromedio("Materiales", materiales);
            imprimirPromedio("Recursos", recursos);
            System.out.println("\n4.3.3 Tareas de usuario completadas");
            System.out.printf("%-22s %10s%n", "Usuario", "Cantidad");
            tareas.forEach((usuario, cantidad) -> System.out.printf("%-22s %10d%n", usuario, cantidad));
        }

        void imprimirPromedio(String nombre, long duracionMs) {
            String media = preparaciones == 0 ? "sin casos"
                    : String.format(Locale.ROOT, "%.3f s", duracionMs / (1000.0 * preparaciones));
            System.out.printf("%-22s %20s%n", nombre, media);
        }
    }

    private static Informe consultar(ProcessEngine motor) {
        var informe = new Informe();
        var historial = motor.getHistoryService();
        for (String grupo : List.of("administracion", "docentes")) {
            motor.getIdentityService().createUserQuery().memberOfGroup(grupo).list()
                    .forEach(usuario -> informe.tareas.put(usuario.getId(), 0L));
        }
        var procesos = historial.createHistoricProcessInstanceQuery().processDefinitionKey(CLAVE)
                .includeProcessVariables().list();
        for (var proceso : procesos) {
            if (proceso.getEndTime() != null && proceso.getDeleteReason() == null) {
                informe.terminadas++;
                String resultado = String.valueOf(proceso.getProcessVariables().get("resultado"));
                String categoria = informe.resultados.containsKey(resultado) ? resultado : "SIN_CLASIFICAR";
                informe.resultados.merge(categoria, 1L, Long::sum);
            }
            var actividades = historial.createHistoricActivityInstanceQuery().processInstanceId(proceso.getId())
                    .finished().list().stream().filter(a -> a.getDeleteReason() == null).collect(Collectors.toList());
            actividades.stream().filter(a -> "userTask".equals(a.getActivityType())).map(HistoricActivityInstance::getAssignee)
                    .filter(Objects::nonNull).forEach(usuario -> informe.tareas.computeIfPresent(usuario, (id, n) -> n + 1));
            informe.medirPreparacion(actividades);
        }
        return informe;
    }

    private static Long intervalo(List<HistoricActivityInstance> actividades, String inicio, String fin) {
        var desde = actividades.stream().filter(a -> inicio.equals(a.getActivityId()))
                .map(HistoricActivityInstance::getStartTime).filter(Objects::nonNull).mapToLong(Date::getTime).min();
        var hasta = actividades.stream().filter(a -> fin.equals(a.getActivityId()))
                .map(HistoricActivityInstance::getEndTime).filter(Objects::nonNull).mapToLong(Date::getTime).max();
        return desde.isPresent() && hasta.isPresent() ? Math.max(0, hasta.getAsLong() - desde.getAsLong()) : null;
    }

    public static void main(String[] args) {
        ProcessEngine motor = ConexionFlowable.abrir();
        try {
            consultar(motor).imprimir();
        } finally {
            motor.close();
        }
    }
}
