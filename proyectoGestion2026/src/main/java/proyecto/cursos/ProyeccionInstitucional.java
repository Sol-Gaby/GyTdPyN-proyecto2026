package proyecto.cursos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class ProyeccionInstitucional {
    private final String origen;
    private final String destino;
    private final String usuario;
    private final String clave;

    ProyeccionInstitucional(String origen, String destino, String usuario, String clave) {
        this.origen = origen;
        this.destino = destino;
        this.usuario = usuario;
        this.clave = clave;
    }

    public static void main(String[] args) throws SQLException {
        System.out.println("Estados institucionales sincronizados: " + desdeEntorno().procesar());
    }

    static ProyeccionInstitucional desdeEntorno() {
        return new ProyeccionInstitucional(requerir("SPRING_DATASOURCE_URL"),
                requerir("CURSOS_INSTITUCION_JDBC_URL"), requerir("SPRING_DATASOURCE_USERNAME"),
                requerir("SPRING_DATASOURCE_PASSWORD"));
    }

    int procesar() throws SQLException {
        String seleccionar = "SELECT * FROM cursos_pendientes ORDER BY revision LIMIT 1 FOR UPDATE";
        int total = 0;
        try (Connection fuente = DriverManager.getConnection(origen, usuario, clave);
                PreparedStatement siguiente = fuente.prepareStatement(seleccionar);
                PreparedStatement retirar = fuente.prepareStatement("DELETE FROM cursos_pendientes WHERE revision = ?")) {
            fuente.setAutoCommit(false);
            while (total < 100) {
                long revision;
                try (ResultSet evento = siguiente.executeQuery()) {
                    if (!evento.next()) {
                        fuente.commit();
                        return total;
                    }
                    revision = evento.getLong("revision");
                    aplicar(evento);
                }
                retirar.setLong(1, revision);
                retirar.executeUpdate();
                fuente.commit();
                total++;
            }
        }
        return total;
    }

    private void aplicar(ResultSet evento) throws SQLException {
        String insertarEmpresa = "INSERT INTO empresas(codigo, nombre, acceso_habilitado) VALUES (?, ?, ?) "
                + "ON CONFLICT (codigo) DO UPDATE SET acceso_habilitado = empresas.acceso_habilitado OR EXCLUDED.acceso_habilitado";
        String guardarCurso = "INSERT INTO cursos(proceso_id, empresa_codigo, nombre, descripcion, programa, "
                + "presupuesto, fechas_posibles, fecha_inicio, informe, estado, revision, solicitud_web) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (proceso_id) DO UPDATE SET "
                + "empresa_codigo = EXCLUDED.empresa_codigo, nombre = EXCLUDED.nombre, "
                + "descripcion = EXCLUDED.descripcion, programa = EXCLUDED.programa, presupuesto = EXCLUDED.presupuesto, "
                + "fechas_posibles = EXCLUDED.fechas_posibles, fecha_inicio = EXCLUDED.fecha_inicio, "
                + "informe = EXCLUDED.informe, estado = EXCLUDED.estado, revision = EXCLUDED.revision, solicitud_web = EXCLUDED.solicitud_web "
                + "WHERE cursos.revision < EXCLUDED.revision";
        try (Connection base = DriverManager.getConnection(destino, usuario, clave);
                PreparedStatement empresa = base.prepareStatement(insertarEmpresa);
                PreparedStatement curso = base.prepareStatement(guardarCurso)) {
            base.setAutoCommit(false);
            empresa.setString(1, evento.getString("codigo_empresa"));
            empresa.setString(2, evento.getString("nombre_empresa"));
            empresa.setBoolean(3, evento.getBoolean("habilitar_empresa"));
            empresa.executeUpdate();
            curso.setString(1, evento.getString("proceso_id"));
            curso.setString(2, evento.getString("codigo_empresa"));
            curso.setString(3, evento.getString("nombre_curso"));
            curso.setString(4, evento.getString("descripcion"));
            curso.setString(5, evento.getString("programa"));
            curso.setBigDecimal(6, evento.getBigDecimal("presupuesto"));
            curso.setString(7, evento.getString("fechas_posibles"));
            curso.setObject(8, evento.getObject("fecha_inicio", OffsetDateTime.class));
            curso.setString(9, evento.getString("informe"));
            curso.setString(10, evento.getString("estado"));
            curso.setLong(11, evento.getLong("revision"));
            curso.setString(12, evento.getString("solicitud_web"));
            curso.executeUpdate();
            guardarDocumento(base, evento);
            base.commit();
        }
    }

    private void guardarDocumento(Connection base, ResultSet evento) throws SQLException {
        String tipo = evento.getString("documento_tipo");
        if (tipo == null) return;
        String cuerpo = "PROPUESTA".equals(tipo)
                ? evento.getString("programa") + "\nPresupuesto: " + evento.getBigDecimal("presupuesto")
                        + "\nFechas posibles: " + evento.getString("fechas_posibles")
                : evento.getString("informe");
        try (PreparedStatement documento = base.prepareStatement("INSERT INTO documentos_curso "
                + "(proceso_id, tipo, revision, contenido) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            documento.setString(1, evento.getString("proceso_id"));
            documento.setString(2, tipo);
            documento.setLong(3, evento.getLong("revision"));
            documento.setBytes(4, DocumentosCurso.pdf(tipo + " · " + evento.getString("nombre_curso") + "\n\n" + cuerpo));
            documento.executeUpdate();
        } catch (java.io.IOException ex) {
            throw new SQLException("No se pudo generar el documento del curso", ex);
        }
    }

    private static String requerir(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Definí " + nombre + " en el contenedor");
        }
        return valor;
    }
}
