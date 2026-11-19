package proyecto.cursos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.IdentityService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/cursos")
public class PortalCursos {
    private static final String CONSULTA_TAREAS = "/runtime/tasks?processDefinitionKey=Process_Curso&";
    private final Institucion institucion;
    private final MotorRest motor;
    private final ProyeccionInstitucional proyeccion;
    private final SeguridadCursos seguridad;
    private final ObjectMapper json = new ObjectMapper();

    public PortalCursos(Institucion institucion, MotorRest motor, IdentityService identidad,
            ProyeccionInstitucional proyeccion) {
        this.institucion = institucion;
        this.motor = motor;
        this.proyeccion = proyeccion;
        this.seguridad = new SeguridadCursos(identidad, institucion);
    }

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public Resource pagina() {
        return new ClassPathResource("portal/index.html");
    }

    @GetMapping(value = "/app.js", produces = "application/javascript;charset=UTF-8")
    public Resource javascript() {
        return new ClassPathResource("portal/app.js");
    }

    @GetMapping(value = "/estilos.css", produces = "text/css;charset=UTF-8")
    public Resource estilos() {
        return new ClassPathResource("portal/estilos.css");
    }

    @PostMapping(value = "/api/solicitudes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> registrar(@RequestBody Map<String, Object> formulario) throws IOException {
        var datos = new LinkedHashMap<String, Object>();
        String referencia = "web-" + UUID.fromString(texto(formulario, "solicitudId", 36));
        for (var dato : Map.of("empresaCodigo", 50, "empresaNombre", 150, "contacto", 150, "email", 150,
                "telefono", 50, "cursoNombre", 150, "cursoDescripcion", 2000, "fechasPreferidas", 400).entrySet()) {
            datos.put(dato.getKey(), texto(formulario, dato.getKey(), dato.getValue()));
        }
        // Orden explícito para la comparación de reintentos del mismo formulario.
        datos = new LinkedHashMap<>(new java.util.TreeMap<>(datos));
        if (!datos.get("empresaCodigo").toString().matches("[A-Z0-9][A-Z0-9-]{0,49}")) {
            throw new IllegalArgumentException("Usá un código de empresa con mayúsculas, números o guion");
        }
        if (!datos.get("email").toString().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Email inválido");
        }
        int participantes = Integer.parseInt(String.valueOf(formulario.get("participantesEstimados")));
        if (participantes <= 0) throw new IllegalArgumentException("La cantidad estimada debe ser positiva");
        datos.put("participantesEstimados", participantes);
        String solicitud = json.writeValueAsString(datos);
        String instancia = recuperar(referencia, solicitud);
        if (instancia == null) {
            var variables = new ArrayList<Map<String, String>>();
            for (String campo : List.of("empresaCodigo", "empresaNombre", "cursoNombre", "cursoDescripcion")) {
                variables.add(Map.of("name", campo, "type", "string", "value", datos.get(campo).toString()));
            }
            variables.add(Map.of("name", "solicitudWeb", "type", "string", "value", solicitud));
            try {
                instancia = motor.llamar("POST", "/runtime/process-instances", Map.of(
                        "message", "Solicitud de curso", "businessKey", referencia, "variables", variables)).path("id").asText();
            } catch (MotorRest.ErrorMotor | IllegalStateException ex) {
                instancia = recuperar(referencia, solicitud);
                if (instancia == null) throw ex;
            }
        }
        confirmarRegistroInstitucional(instancia);
        return Map.of("processInstanceId", instancia, "referenciaSolicitud", referencia,
                "usuarioEmpresa", "empresa-" + datos.get("empresaCodigo"));
    }

    private void confirmarRegistroInstitucional(String instancia) {
        String error = "La sincronización de la solicitud está pendiente. Reintentá sin cambiar los datos.";
        try {
            if (institucion.tieneSolicitud(instancia)) return;
            proyeccion.procesar(instancia);
            if (institucion.tieneSolicitud(instancia)) return;
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error, ex);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error);
    }

    private String recuperar(String referencia, String solicitud) {
        var previas = motor.listar("/history/historic-process-instances?processDefinitionKey=Process_Curso"
                + "&includeProcessVariables=true&businessKey=" + MotorRest.codificar(referencia));
        if (previas.isEmpty()) return null;
        JsonNode proceso = previas.get(0);
        for (JsonNode var : proceso.path("variables")) {
            if ("solicitudWeb".equals(var.path("name").asText()) && solicitud.equals(var.path("value").asText())) {
                return proceso.path("id").asText();
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "La referencia pertenece a otra solicitud");
    }

    @GetMapping("/api/yo")
    public Map<String, String> yo(Authentication usuario) {
        return Map.of("usuario", usuario.getName(), "rol", esEmpresa(usuario) ? "EMPRESA" : "PERSONAL");
    }

    @GetMapping("/api/tareas")
    public List<JsonNode> tareas(Principal usuario) {
        String id = MotorRest.codificar(usuario.getName());
        var disponibles = new LinkedHashMap<String, JsonNode>();
        List<JsonNode> filas = new ArrayList<>(motor.listar(CONSULTA_TAREAS + "assignee=" + id));
        if (seguridad.pertenece(usuario.getName(), TareasCurso.ADMINISTRACION)) {
            motor.listar(CONSULTA_TAREAS + "candidateGroup=administracion").stream()
                    .filter(t -> t.path("assignee").isNull() || t.path("assignee").isMissingNode())
                    .forEach(filas::add);
        }
        for (var fila : filas) {
            if (PermisosRest.CAMPOS.containsKey(fila.path("taskDefinitionKey").asText())) {
                disponibles.put(fila.path("id").asText(), fila);
            }
        }
        return new ArrayList<>(disponibles.values());
    }

    @GetMapping("/api/tareas/{id}/datos")
    public Map<String, String> datos(@PathVariable("id") String id, Principal usuario) {
        var tarea = tareaDisponible(id, usuario);
        var campos = new LinkedHashMap<String, String>();
        String claveTarea = tarea.path("taskDefinitionKey").asText();
        List<String> visibles;
        switch (claveTarea) {
            case "Propuesta":
            case "Revisar":
                visibles = List.of("programa", "presupuesto", "fechasPosibles", "observaciones");
                break;
            case "Lista":
                visibles = List.of("nomina");
                break;
            case "Informe":
            case "EnviarInforme":
                visibles = List.of("informeResultados");
                break;
            default:
                return campos;
        }
        String instancia = tarea.path("processInstanceId").asText();
        JsonNode variables = motor.llamar("GET", "/runtime/process-instances/" + MotorRest.codificar(instancia)
                + "/variables", null);
        for (var variable : variables) {
            String nombre = variable.path("name").asText();
            if (visibles.contains(nombre)) {
                campos.put(nombre, variable.path("value").asText());
            }
        }
        return campos;
    }

    @PostMapping(value = "/api/tareas/{id}/{accion}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actuar(@PathVariable("id") String id, @PathVariable("accion") String accion,
            @RequestBody Map<String, Object> valores, Principal usuario, @RequestHeader("Authorization") String auth) {
        var tarea = tareaDisponible(id, usuario);
        String responsable = tarea.path("assignee").asText("");
        String tareaId = MotorRest.codificar(id);
        String clave = claveSesion(auth);
        String codigo = tarea.path("taskDefinitionKey").asText();
        if ("reclamar".equals(accion)) {
            if (!responsable.isEmpty() || !valores.isEmpty()) throw new IllegalArgumentException("La tarea ya está asignada");
            motor.tareaComo(usuario.getName(), clave, "PUT", "tasks/" + tareaId + "/action/claim", Map.of());
            return;
        }
        if (!"completar".equals(accion)) throw new IllegalArgumentException("Acción desconocida");
        if (!usuario.getName().equals(responsable)) throw prohibido();
        PermisosRest.validar(codigo, valores);
        if (PermisosRest.CAMPOS.get(codigo).isEmpty()) {
            motor.tareaComo(usuario.getName(), clave, "PUT", "tasks/" + tareaId + "/action/complete", Map.of());
        } else {
            String ruta = "task-forms/" + tareaId;
            String form = motor.tareaComo(usuario.getName(), clave, "GET", ruta, null).path("id").asText();
            motor.tareaComo(usuario.getName(), clave, "POST", ruta, Map.of("formId", form, "values", valores));
        }
    }

    private JsonNode tareaDisponible(String id, Principal usuario) {
        return tareas(usuario).stream().filter(t -> id.equals(t.path("id").asText())).findFirst()
                .orElseThrow(PortalCursos::prohibido);
    }

    @GetMapping("/api/empresa/cursos")
    public List<Map<String, Object>> cursos(Principal usuario) throws SQLException {
        var cursos = institucion.cursos(codigoEmpresa(usuario));
        for (var curso : cursos) {
            if (!"EN_TRAMITE".equals(curso.get("estado"))) continue;
            String id = MotorRest.codificar((String) curso.get("id"));
            var tareas = motor.listar("/runtime/tasks?processInstanceId=" + id);
            var ejecuciones = motor.listar("/runtime/executions?processInstanceId=" + id);
            var actividades = new LinkedHashSet<String>();
            var claves = new LinkedHashSet<String>();
            tareas.forEach(t -> {
                actividades.add(t.path("name").asText());
                claves.add(t.path("taskDefinitionKey").asText());
            });
            ejecuciones.forEach(e -> {
                String actividad = e.path("activityId").asText();
                claves.add(actividad);
                if ("Nomina".equals(actividad)) actividades.add("Esperar la nómina de participantes");
            });
            String estado = "EN_PREPARACION";
            if (claves.contains("Activity_1m08pcl") || claves.contains("CompensarPreparacion")
                    || claves.contains("ComprobarEquipoLiberado")) estado = "LIBERANDO_RECURSOS";
            else if (claves.contains("Informe") || claves.contains("EnviarInforme")) estado = "EN_ELABORACION_DEL_INFORME";
            else if (claves.contains("Dictar")) estado = "EN_DESARROLLO";
            else if (claves.contains("Activity_1018bdz") || claves.contains("Fecha")) estado = "CONFIRMADO_PENDIENTE_INICIO";
            curso.put("estado", estado);
            curso.put("actividades", actividades);
        }
        return cursos;
    }

    @GetMapping("/api/documentos/{id}/{tipo}")
    public ResponseEntity<byte[]> documento(@PathVariable("id") String id, @PathVariable("tipo") String tipo,
            Authentication usuario) throws SQLException {
        if (!List.of("PROPUESTA", "INFORME").contains(tipo)) return ResponseEntity.notFound().build();
        String empresa = esEmpresa(usuario) ? codigoEmpresa(usuario) : null;
        if (empresa == null && !List.of("jefeAdmin", "jefeDocente").contains(usuario.getName())) throw prohibido();
        byte[] pdf = institucion.documento(id, tipo, empresa);
        return pdf == null ? ResponseEntity.notFound().build()
                : ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> respuestaDeError(ResponseStatusException error) {
        String mensaje = error.getReason() == null ? error.getStatus().getReasonPhrase() : error.getReason();
        return ResponseEntity.status(error.getStatus()).body(Map.of("error", mensaje));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException error) {
        int estado = error instanceof MotorRest.ErrorMotor ? ((MotorRest.ErrorMotor) error).estado() : 400;
        return ResponseEntity.status(estado >= 500 ? 502 : estado).body(Map.of("error", error.getMessage()));
    }

    private static ResponseStatusException prohibido() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "La tarea o documento no corresponde a tu cuenta");
    }

    private static boolean esEmpresa(Authentication usuario) {
        return usuario.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));
    }

    private static String codigoEmpresa(Principal usuario) {
        return usuario.getName().substring("empresa-".length());
    }

    private static String texto(Map<String, Object> formulario, String campo, int limite) {
        Object valor = formulario.get(campo);
        if (!(valor instanceof String) || ((String) valor).isBlank() || ((String) valor).trim().length() > limite) {
            throw new IllegalArgumentException("Completá " + campo + " (hasta " + limite + " caracteres)");
        }
        return ((String) valor).trim();
    }

    private static String claveSesion(String auth) {
        if (!auth.regionMatches(true, 0, "Basic ", 0, 6)) throw prohibido();
        String datos = new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8);
        int separador = datos.indexOf(':');
        if (separador < 0) throw prohibido();
        return datos.substring(separador + 1);
    }
}
