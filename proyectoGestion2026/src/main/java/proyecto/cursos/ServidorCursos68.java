package proyecto.cursos;

import org.flowable.engine.IdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cursos.institucion.jdbc-url")
@Import({ControladorPortalCursos.class, ServidorCursos68.SeguridadPortal.class})
public class ServidorCursos68 {
    @Bean
    RepositorioInstitucional institucion(@Value("${cursos.institucion.jdbc-url}") String jdbc,
            @Value("${spring.datasource.username}") String usuario, @Value("${spring.datasource.password}") String clave) {
        return new RepositorioInstitucional(jdbc, usuario, clave);
    }

    @Bean
    ClienteRestFlowable motorRest(@Value("${cursos.rest.url:http://127.0.0.1:8080/flowable-ui}") String url,
            @Value("${cursos.rest.usuario:admin}") String usuario, @Value("${cursos.rest.clave:test}") String clave) {
        return new ClienteRestFlowable(url, usuario, clave);
    }

    @Configuration(proxyBeanMethods = false)
    @Order(-10)
    @SuppressWarnings("deprecation")
    static class SeguridadPortal extends WebSecurityConfigurerAdapter {
        private final AutenticacionPortalCursos seguridad;

        SeguridadPortal(IdentityService identidad, RepositorioInstitucional institucion) {
            this.seguridad = new AutenticacionPortalCursos(identidad, institucion);
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.antMatcher("/cursos/**").authenticationManager(new ProviderManager(seguridad)).csrf().disable()
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                    .authorizeRequests()
                    .antMatchers("/cursos/", "/cursos/app.js", "/cursos/estilos.css", "/cursos/api/solicitudes").permitAll()
                    .antMatchers("/cursos/api/empresa/**").hasAuthority("ROLE_EMPRESA")
                    .antMatchers("/cursos/api/tareas/**").hasAuthority("ROLE_PERSONAL")
                    .antMatchers("/cursos/api/yo", "/cursos/api/documentos/**").hasAnyAuthority("ROLE_EMPRESA", "ROLE_PERSONAL")
                    .anyRequest().denyAll().and().httpBasic(Customizer.withDefaults());
        }
    }
}
