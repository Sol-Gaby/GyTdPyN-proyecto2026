package proyecto.cursos;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cursos.institucion.jdbc-url")
public class ConfiguracionInstitucional {
    private static final Logger LOG = Logger.getLogger(ConfiguracionInstitucional.class.getName());

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService sincronizadorInstitucional() {
        ProyeccionInstitucional proyeccion = ProyeccionInstitucional.desdeEntorno();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "proyeccion-institucional");
            hilo.setDaemon(true);
            return hilo;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                proyeccion.procesar();
            } catch (SQLException ex) {
                LOG.warning("Sincronización institucional pendiente. SQLState " + ex.getSQLState());
            }
        }, 2, 2, TimeUnit.SECONDS);
        return executor;
    }
}
