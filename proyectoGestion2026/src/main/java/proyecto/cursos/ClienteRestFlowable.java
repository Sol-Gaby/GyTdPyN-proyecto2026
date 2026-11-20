package proyecto.cursos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ClienteRestFlowable {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String url;
    private final String tecnico;

    public ClienteRestFlowable(String url, String usuario, String clave) {
        this.url = url.replaceAll("/+$", "");
        this.tecnico = "Basic " + Base64.getEncoder().encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
    }

    public JsonNode llamar(String metodo, String ruta, Object cuerpo) {
        return solicitar(http, metodo, "/process-api" + ruta, cuerpo, tecnico);
    }

    public List<JsonNode> listar(String ruta) {
        List<JsonNode> filas = new ArrayList<>();
        int inicio = 0;
        while (true) {
            var pagina = llamar("GET", ruta + (ruta.contains("?") ? "&" : "?") + "start=" + inicio + "&size=100", null);
            for (var fila : pagina.path("data")) filas.add(fila);
            int cantidad = pagina.path("data").size();
            inicio += cantidad;
            if (cantidad == 0 || inicio >= pagina.path("total").asInt()) return filas;
        }
    }

    JsonNode tareaComo(String usuario, String clave, String metodo, String ruta, Object datos) {
        HttpClient sesion = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(5)).build();
        solicitar(sesion, "POST", "/app/authentication",
                "j_username=" + codificar(usuario) + "&j_password=" + codificar(clave) + "&submit=Login", null);
        try {
            return solicitar(sesion, metodo, "/app/rest/" + ruta, datos, null);
        } finally {
            try {
                solicitar(sesion, "GET", "/app/logout", null, null);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // La cookie se descarta con este cliente. El logout no cambia el resultado de la tarea.
            }
        }
    }

    private JsonNode solicitar(HttpClient cliente, String metodo, String ruta, Object cuerpo, String auth) {
        try {
            var req = HttpRequest.newBuilder(URI.create(url + ruta)).timeout(Duration.ofSeconds(25));
            if (auth != null) req.header("Authorization", auth);
            String contenido = null;
            if (cuerpo instanceof String) {
                contenido = (String) cuerpo;
                req.header("Content-Type", "application/x-www-form-urlencoded");
            } else if (cuerpo != null) {
                contenido = json.writeValueAsString(cuerpo);
                req.header("Content-Type", "application/json");
            }
            var respuesta = cliente.send(req.method(metodo, contenido == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(contenido)).build(), HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                throw new ErrorMotor(respuesta.statusCode());
            }
            if (respuesta.body().isBlank() || !respuesta.headers().firstValue("Content-Type").orElse("").contains("json")) {
                return json.nullNode();
            }
            return json.readTree(respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Comunicación interrumpida", e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo contactar Flowable", e);
        }
    }

    public static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    public static final class ErrorMotor extends IllegalArgumentException {
        private final int estado;
        ErrorMotor(int estado) {
            super("Flowable respondió HTTP " + estado);
            this.estado = estado;
        }
        public int estado() { return estado; }
    }
}
