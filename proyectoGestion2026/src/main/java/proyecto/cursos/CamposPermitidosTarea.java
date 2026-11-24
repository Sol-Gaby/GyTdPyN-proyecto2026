package proyecto.cursos;

import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Los formularios del portal solo pueden cambiar campos de la tarea correspondiente. */
final class CamposPermitidosTarea {
    static final Map<String, Set<String>> CAMPOS = Map.of(
            "reg_elav_propuesta", Set.of("programa", "presupuesto", "fechasPosibles"),
            "revisar_propuesta", Set.of("programa", "presupuesto", "fechasPosibles"),
            "archivar_solicitud", Set.of(), "confeccion_lista", Set.of(),
            "evaluar_recursos", Set.of("requiereAula", "requierePlataforma", "requiereEquipamiento"),
            "envio_Informe", Set.of(), "definir_docente_resp", Set.of("docenteAsignado"),
            "dictar_curso", Set.of(), "Informe", Set.of("informeResultados"));

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
