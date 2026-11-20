package proyecto.cursos;

import java.util.List;
import org.flowable.engine.IdentityService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

final class AutenticacionPortalCursos implements AuthenticationProvider {
    private final IdentityService identidad;
    private final RepositorioInstitucional institucion;

    AutenticacionPortalCursos(IdentityService identidad, RepositorioInstitucional institucion) {
        this.identidad = identidad;
        this.institucion = institucion;
    }

    @Override
    public Authentication authenticate(Authentication solicitud) {
        String usuario = solicitud.getName();
        String clave = String.valueOf(solicitud.getCredentials());
        boolean empresa = usuario.startsWith("empresa-");
        boolean habilitado = empresa
                ? institucion.autenticarEmpresa(usuario.substring("empresa-".length()), clave)
                : (pertenece(usuario, TareasCurso.ADMINISTRACION) || pertenece(usuario, TareasCurso.DOCENTES))
                    && identidad.checkPassword(usuario, clave);
        if (!habilitado) throw new BadCredentialsException("Credenciales inválidas");
        return new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority(empresa ? "ROLE_EMPRESA" : "ROLE_PERSONAL")));
    }

    boolean pertenece(String usuario, String grupo) {
        return identidad.createUserQuery().userId(usuario).memberOfGroup(grupo).count() == 1;
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }
}
