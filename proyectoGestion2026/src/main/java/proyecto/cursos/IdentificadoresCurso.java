package proyecto.cursos;

import java.util.Map;

/** Traduce los IDs de instancias antiguas al modelo vigente sin modificar su definición desplegada. */
public final class IdentificadoresCurso {
    private static final Map<String, String> ANTERIORES = Map.ofEntries(
            Map.entry("Recordatorio", "envio_recordatorio"),
            Map.entry("Archivar", "archivar_solicitud"),
            Map.entry("Dictar", "dictar_curso"),
            Map.entry("Fecha", "fecha_programada"),
            Map.entry("Nomina", "recibe_nomina"),
            Map.entry("Lista", "confeccion_lista"),
            Map.entry("Material", "Materiales"),
            Map.entry("Equipo", "contrato_equipo"),
            Map.entry("Activity_12i2hpf", "evaluar_recursos"),
            Map.entry("VerificarCompensaciones", "verificar_compensaciones"),
            Map.entry("Activity_1m08pcl", "liberar_equipo"),
            Map.entry("ValidarNomina", "validar_nomina"),
            Map.entry("Propuesta", "reg_elav_propuesta"),
            Map.entry("Activity_0iibzwu", "notificar_inviabilidad"),
            Map.entry("Confirmar", "envio_confirmacion_curso"),
            Map.entry("Revisar", "revisar_propuesta"),
            Map.entry("EnviarInforme", "envio_Informe"),
            Map.entry("Activity_1018bdz", "definir_docente_resp"),
            Map.entry("Activity_1eg7zcl", "habilita_proceso_empresa"),
            Map.entry("Enviar", "enviar_propuesta"),
            Map.entry("ContarCursos", "contar_cursos"),
            Map.entry("ValidarModificaciones", "reg_observaciones"),
            Map.entry("CompensarPreparacion", "liberar_recursos"),
            Map.entry("Activity_12e0et1", "notificar_cierre"));

    private IdentificadoresCurso() { }

    public static String actual(String id) {
        return ANTERIORES.getOrDefault(id, id);
    }
}
