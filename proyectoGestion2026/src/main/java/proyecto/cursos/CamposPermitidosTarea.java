package proyecto.cursos;

import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Los formularios del portal solo pueden cambiar campos de la tarea correspondiente. */
final class CamposPermitidosTarea {
    static final Map<String, Set<String>> CAMPOS = Map.of(
            "Propuesta", Set.of("programa", "presupuesto", "fechasPosibles"),
            "Revisar", Set.of("programa", "presupuesto", "fechasPosibles"),
            "Archivar", Set.of(), "Lista", Set.of(),
            "Activity_12i2hpf", Set.of("requiereAula", "requierePlataforma", "requiereEquipamiento"),
            "EnviarInforme", Set.of(), "Activity_1018bdz", Set.of("docenteAsignado"),
            "Dictar", Set.of(), "Informe", Set.of("informeResultados"));

    private CamposPermitidosTarea() { }

    static void validar(String tarea, Map<String, Object> valores) {
        Set<String> permitidos = CAMPOS.get(tarea);
        if (permitidos == null || !permitidos.containsAll(valores.keySet())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Variable o tarea fuera del formulario");
        }
        for (Object valor : valores.values()) {
            if (!(valor instanceof String || valor instanceof Number || valor instanceof Boolean)) {
                throw new IllegalArgumentException("Valor no válido para el formulario");
            }
        }
    }
}
