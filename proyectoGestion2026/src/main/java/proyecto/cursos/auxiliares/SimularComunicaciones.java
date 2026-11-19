package proyecto.cursos.auxiliares;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import proyecto.cursos.MotorRest;

/** Simula los mensajes entre la empresa y el proceso mediante la API REST. */
public final class SimularComunicaciones {
    private static final List<String> MENSAJES = List.of("Aceptación", "Rechazo", "Solicitud de modificaciones",
            "Nómina de participantes", "Cancelación");

    public static void main(String[] args) {
        String clave = System.getenv("CURSOS_REST_CLAVE");
        if (clave == null || clave.isBlank()) throw new IllegalStateException("Definí CURSOS_REST_CLAVE");
        MotorRest motor = new MotorRest(System.getenv().getOrDefault("CURSOS_REST_URL", "http://localhost:8082/flowable-ui"),
                System.getenv().getOrDefault("CURSOS_REST_USUARIO", "admin"), clave);
        try (Scanner entrada = new Scanner(System.in)) {
            while (true) {
                var procesos = motor.listar("/runtime/process-instances?processDefinitionKey=Process_Curso");
                if (procesos.isEmpty()) {
                    System.out.println("No hay solicitudes activas.");
                    return;
                }
                var ids = new LinkedHashMap<String, String>();
                System.out.println("\nSolicitudes activas");
                for (JsonNode proceso : procesos) {
                    String indice = String.valueOf(ids.size() + 1);
                    String id = proceso.path("id").asText();
                    ids.put(indice, id);
                    System.out.println(indice + ". " + id + " " + proceso.path("businessKey").asText(""));
                }
                System.out.print("Elegí una solicitud (0 para salir): ");
                String opcion = entrada.nextLine().trim();
                if ("0".equals(opcion)) return;
                String id = ids.get(opcion);
                if (id == null) {
                    System.out.println("Elegí un número de la lista.");
                    continue;
                }
                try {
                    operar(entrada, motor, id);
                } catch (IllegalArgumentException | java.time.DateTimeException ex) {
                    System.out.println("El mensaje no se envió: " + ex.getMessage());
                }
            }
        }
    }

    private static void operar(Scanner entrada, MotorRest motor, String id) {
        var disponibles = new LinkedHashMap<String, String>();
        for (String mensaje : MENSAJES) {
            var filas = motor.listar("/runtime/executions?processInstanceId=" + MotorRest.codificar(id)
                    + "&messageEventSubscriptionName=" + MotorRest.codificar(mensaje));
            if (filas.size() == 1) disponibles.put(mensaje, filas.get(0).path("id").asText());
        }
        if (disponibles.isEmpty()) {
            System.out.println("La solicitud todavía no espera una comunicación.");
            return;
        }
        var opciones = List.copyOf(disponibles.keySet());
        for (int i = 0; i < opciones.size(); i++) System.out.println((i + 1) + ". " + opciones.get(i));
        System.out.print("Elegí un mensaje (0 para volver): ");
        int opcion = Integer.parseInt(entrada.nextLine().trim());
        if (opcion == 0) return;
        if (opcion < 1 || opcion > opciones.size()) throw new IllegalArgumentException("Opción desconocida");
        String mensaje = opciones.get(opcion - 1);
        var datos = new java.util.ArrayList<Map<String, String>>();
        switch (mensaje) {
            case "Aceptación":
                datos.add(variable("fechaCurso", OffsetDateTime.parse(pedir(entrada, "Fecha ISO 8601 con zona")).toString()));
                break;
            case "Solicitud de modificaciones":
                datos.add(variable("observaciones", pedir(entrada, "Observaciones")));
                break;
            case "Nómina de participantes":
                String texto = pedir(entrada, "Participantes separados por coma");
                var nombres = List.of(texto.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
                if (nombres.isEmpty()) throw new IllegalArgumentException("Ingresá al menos un participante");
                try {
                    datos.add(variable("nomina", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(nombres)));
                } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    throw new IllegalArgumentException("Nómina no válida", ex);
                }
                break;
            default:
                break;
        }
        motor.llamar("PUT", "/runtime/executions/" + MotorRest.codificar(disponibles.get(mensaje)),
                Map.of("action", "messageEventReceived", "messageName", mensaje, "variables", datos));
        System.out.println("Mensaje enviado: " + mensaje);
    }

    private static String pedir(Scanner entrada, String etiqueta) {
        System.out.print(etiqueta + ": ");
        String valor = entrada.nextLine().trim();
        if (valor.isEmpty()) throw new IllegalArgumentException("Falta " + etiqueta);
        return valor;
    }

    private static Map<String, String> variable(String nombre, String valor) {
        return Map.of("name", nombre, "type", "string", "value", valor);
    }
}
