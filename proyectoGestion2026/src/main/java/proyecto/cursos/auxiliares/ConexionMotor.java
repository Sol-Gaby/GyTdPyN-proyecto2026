package proyecto.cursos.auxiliares;

import java.util.TimeZone;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;

public final class ConexionMotor {
    public static ProcessEngine abrir() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        var cfg = ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
                .setEngineName("auxiliar68")
                .setJdbcDriver("org.postgresql.Driver")
                .setJdbcUrl(variable("CURSOS_JDBC", "jdbc:postgresql://localhost:5433/flowable"))
                .setJdbcUsername(variable("CURSOS_BD_USUARIO", "flowable"))
                .setJdbcPassword(exigirClave())
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE)
                .setAsyncExecutorActivate(false).setHistory("audit");
        cfg.addConfigurator(new org.flowable.dmn.engine.configurator.DmnEngineConfigurator());
        // No despliega modelos ni ejecuta temporizadores: trabaja sobre las tareas del servidor.
        return cfg.buildProcessEngine();
    }
    private static String exigirClave() {
        String clave = System.getenv("CURSOS_BD_CLAVE");
        if (clave == null || clave.isBlank()) throw new IllegalStateException("Definí CURSOS_BD_CLAVE");
        return clave;
    }
    private static String variable(String nombre, String defecto) { return System.getenv().getOrDefault(nombre, defecto); }
}
