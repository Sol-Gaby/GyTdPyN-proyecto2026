package proyecto.cursos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class RepositorioInstitucional {
    private final String jdbc;
    private final String usuario;
    private final String clave;
    private final BCryptPasswordEncoder cifrado = new BCryptPasswordEncoder();

    RepositorioInstitucional(String jdbc, String usuario, String clave) {
        this.jdbc = jdbc;
        this.usuario = usuario;
        this.clave = clave;
    }

    boolean autenticarEmpresa(String codigo, String password) {
        String sql = "SELECT clave_portal FROM empresas WHERE codigo = ? AND acceso_habilitado";
        try (var c = abrir(); var q = c.prepareStatement(sql)) {
            q.setString(1, codigo);
            try (var fila = q.executeQuery()) {
                return fila.next() && fila.getString(1) != null && cifrado.matches(password, fila.getString(1));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo consultar la cuenta institucional", ex);
        }
    }

    void establecerClave(String codigo, String password) throws SQLException {
        int bytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (password.isBlank() || bytes < 10 || bytes > 72) {
            throw new IllegalArgumentException("La clave debe tener entre 10 y 72 bytes");
        }
        try (var c = abrir(); var q = c.prepareStatement(
                "UPDATE empresas SET clave_portal = ? WHERE codigo = ? AND acceso_habilitado")) {
            q.setString(1, cifrado.encode(password));
            q.setString(2, codigo);
            if (q.executeUpdate() != 1) {
                throw new IllegalArgumentException("La empresa todavía no tiene acceso habilitado");
            }
        }
    }

    boolean tieneSolicitud(String instancia) throws SQLException {
        String sql = "SELECT 1 FROM cursos WHERE proceso_id = ? AND solicitud_web IS NOT NULL";
        try (var c = abrir(); var q = c.prepareStatement(sql)) {
            q.setString(1, instancia);
            try (var fila = q.executeQuery()) {
                return fila.next();
            }
        }
    }

    List<Map<String, Object>> cursos(String codigo) throws SQLException {
        String sql = "SELECT proceso_id, nombre, descripcion, estado, fecha_inicio FROM cursos "
                + "WHERE empresa_codigo = ? AND fecha_inicio IS NOT NULL ORDER BY revision DESC";
        List<Map<String, Object>> cursos = new ArrayList<>();
        try (var c = abrir(); var q = c.prepareStatement(sql)) {
            q.setString(1, codigo);
            try (var filas = q.executeQuery()) {
                while (filas.next()) {
                    var fila = new LinkedHashMap<String, Object>();
                    fila.put("id", filas.getString("proceso_id"));
                    fila.put("nombre", filas.getString("nombre"));
                    fila.put("descripcion", filas.getString("descripcion"));
                    fila.put("estado", filas.getString("estado"));
                    fila.put("fechaCurso", filas.getString("fecha_inicio"));
                    cursos.add(fila);
                }
            }
        }
        return cursos;
    }

    byte[] documento(String instancia, String tipo, String empresa) throws SQLException {
        String sql = "SELECT d.contenido FROM documentos_curso d JOIN cursos c USING(proceso_id) "
                + "WHERE d.proceso_id = ? AND d.tipo = ? "
                + (empresa == null ? "" : "AND c.empresa_codigo = ? AND c.fecha_inicio IS NOT NULL ")
                + "ORDER BY d.revision DESC LIMIT 1";
        try (var c = abrir(); var q = c.prepareStatement(sql)) {
            q.setString(1, instancia);
            q.setString(2, tipo);
            if (empresa != null) q.setString(3, empresa);
            try (var fila = q.executeQuery()) {
                return fila.next() ? fila.getBytes(1) : null;
            }
        }
    }

    private Connection abrir() throws SQLException {
        return DriverManager.getConnection(jdbc, usuario, clave);
    }
}
