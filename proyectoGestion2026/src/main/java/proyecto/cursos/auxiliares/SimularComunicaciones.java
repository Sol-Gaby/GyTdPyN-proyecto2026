package proyecto.cursos.auxiliares;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import proyecto.cursos.MotorRest;

/** Interfaz de consola y operaciones REST compartidas con la ventana de simulación. */
public final class SimularComunicaciones {
    static final String ACEPTACION = "Aceptación";
    static final String MODIFICACIONES = "Solicitud de modificaciones";
    static final String NOMINA = "Nómina de participantes";
    private static final List<String> MENSAJES = List.of(ACEPTACION, "Rechazo", MODIFICACIONES, NOMINA, "Cancelación");
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        MotorRest motor = conectar();
        try (Scanner entrada = new Scanner(System.in)) {
            while (true) {
                var solicitudes = solicitudes(motor);
                if (solicitudes.isEmpty()) {
                    System.out.println("No hay solicitudes activas.");
                    return;
                }
                try {
                    String seleccion = elegir(entrada, "Solicitudes activas", List.copyOf(solicitudes.keySet()));
                    if (seleccion == null) return;
                    String id = solicitudes.get(seleccion);
                    var mensajes = disponibles(motor, id);
                    if (mensajes.isEmpty()) {
                        System.out.println("La solicitud todavía no espera una comunicación.");
                        continue;
                    }
                    String mensaje = elegir(entrada, "Comunicaciones habilitadas",
                            List.copyOf(mensajes.keySet()));
                    if (mensaje == null) continue;
                    String campo = campo(mensaje);
                    if (campo != null) {
                        System.out.print(campo + ": ");
                        if (!entrada.hasNextLine()) return;
                    }
                    String valor = campo == null ? "" : entrada.nextLine();
                    enviar(motor, mensajes.get(mensaje), mensaje, valor);
                    System.out.println("Mensaje enviado: " + mensaje);
                } catch (IllegalArgumentException | IllegalStateException | java.time.DateTimeException ex) {
                    System.out.println("No se pudo operar: " + ex.getMessage());
                }
            }
        }
    }

    static MotorRest conectar() {
        String clave = System.getenv("CURSOS_REST_CLAVE");
        if (clave == null || clave.isBlank()) throw new IllegalStateException("Definí CURSOS_REST_CLAVE");
        return new MotorRest(System.getenv().getOrDefault("CURSOS_REST_URL", "http://localhost:8082/flowable-ui"),
                System.getenv().getOrDefault("CURSOS_REST_USUARIO", "admin"), clave);
    }

    // El formulario y la consola muestran la misma referencia y el ID que devuelve el portal.
    static Map<String, String> solicitudes(MotorRest motor) {
        var solicitudes = new LinkedHashMap<String, String>();
        for (var proceso : motor.listar("/runtime/process-instances?processDefinitionKey=Process_Curso")) {
            String id = proceso.path("id").asText();
            String etiqueta = id + " " + proceso.path("businessKey").asText("");
            solicitudes.put(etiqueta.trim(), id);
        }
        return solicitudes;
    }

    static Map<String, String> disponibles(MotorRest motor, String id) {
        var disponibles = new LinkedHashMap<String, String>();
        for (String mensaje : MENSAJES) {
            var filas = motor.listar("/runtime/executions?processInstanceId=" + MotorRest.codificar(id)
                    + "&messageEventSubscriptionName=" + MotorRest.codificar(mensaje));
            if (filas.size() == 1) disponibles.put(mensaje, filas.get(0).path("id").asText());
        }
        return disponibles;
    }

    static String campo(String mensaje) {
        switch (mensaje) {
            case ACEPTACION: return "Fecha ISO 8601 con zona";
            case MODIFICACIONES: return "Observaciones";
            case NOMINA: return "Participantes separados por coma";
            default: return null;
        }
    }

    static void enviar(MotorRest motor, String ejecucion, String mensaje, String valor) {
        String dato = valor.trim();
        String campo = campo(mensaje);
        if (campo != null && dato.isEmpty()) throw new IllegalArgumentException("Falta " + campo);
        List<Map<String, String>> variables;
        switch (mensaje) {
            case ACEPTACION:
                variables = List.of(variable("fechaCurso", OffsetDateTime.parse(dato).toString()));
                break;
            case MODIFICACIONES:
                variables = List.of(variable("observaciones", dato));
                break;
            case NOMINA:
                var nombres = Arrays.stream(dato.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                if (nombres.isEmpty()) throw new IllegalArgumentException("Ingresá al menos un participante");
                try {
                    variables = List.of(variable("nomina", JSON.writeValueAsString(nombres)));
                } catch (JsonProcessingException ex) {
                    throw new IllegalArgumentException("Nómina no válida", ex);
                }
                break;
            default:
                variables = List.of();
        }
        motor.llamar("PUT", "/runtime/executions/" + MotorRest.codificar(ejecucion),
                Map.of("action", "messageEventReceived", "messageName", mensaje, "variables", variables));
    }

    private static String elegir(Scanner entrada, String titulo, List<String> etiquetas) {
        System.out.println("\n" + titulo);
        for (int i = 0; i < etiquetas.size(); i++) System.out.println((i + 1) + ". " + etiquetas.get(i));
        System.out.print("Elegí una opción (0 para volver o salir): ");
        if (!entrada.hasNextLine()) return null;
        int indice = Integer.parseInt(entrada.nextLine().trim());
        if (indice == 0) return null;
        if (indice < 1 || indice > etiquetas.size()) throw new IllegalArgumentException("Elegí un número de la lista");
        return etiquetas.get(indice - 1);
    }

    private static Map<String, String> variable(String nombre, String valor) {
        return Map.of("name", nombre, "type", "string", "value", valor);
    }
}
