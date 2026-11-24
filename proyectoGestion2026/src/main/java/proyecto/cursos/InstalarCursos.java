package proyecto.cursos;

import org.flowable.dmn.engine.configurator.DmnEngineConfigurator;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.EngineServiceUtil;
import org.flowable.form.engine.configurator.FormEngineConfigurator;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.api.Privilege;
import org.flowable.idm.api.User;

/** Instala el modelo y los usuarios de demostración en la base del motor. */
public class InstalarCursos {
    public static final String BPMN = "processes/Organizar_curso_de_capacitación.bpmn20.xml";
    public static final String DMN = "decisions/viabilidad-curso.dmn";

    public static void main(String[] args) {
        ProcessEngineConfigurationImpl configuracion = (ProcessEngineConfigurationImpl)
                ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration();
        configuracion.setJdbcDriver("org.postgresql.Driver");
        configuracion.setJdbcUrl(requerirEntorno("SPRING_DATASOURCE_URL"));
        configuracion.setJdbcUsername(requerirEntorno("SPRING_DATASOURCE_USERNAME"));
        configuracion.setJdbcPassword(requerirEntorno("SPRING_DATASOURCE_PASSWORD"));
        configuracion.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
        configuracion.setAsyncExecutorActivate(false);
        String clavePersonal = requerirEntorno("CURSOS_PERSONAL_PASSWORD");

        DmnEngineConfigurator dmn = new DmnEngineConfigurator();
        configuracion.addConfigurator(dmn);
        FormEngineConfigurator formularios = new FormEngineConfigurator();
        configuracion.addConfigurator(formularios);
        ProcessEngine motor = configuracion.buildProcessEngine();
        try {
            crearPersonal(EngineServiceUtil.getIdmIdentityService(configuracion), clavePersonal);
            formularios.getFormEngineConfiguration().getFormRepositoryService()
                    .createDeployment().name("Formularios de cursos").enableDuplicateFiltering()
                    .addClasspathResource("forms/propuesta.form").addClasspathResource("forms/recursos.form")
                    .addClasspathResource("forms/docente.form").addClasspathResource("forms/informe.form").deploy();
            String despliegue = desplegar(motor, dmn, true);
            System.out.println("Process_Curso disponible. Despliegue BPMN: " + despliegue);
        } finally {
            motor.close();
        }
    }

    static String desplegar(ProcessEngine motor, DmnEngineConfigurator dmn, boolean evitarDuplicados) {
        var decisiones = dmn.getDmnEngineConfiguration().getDmnRepositoryService()
                .createDeployment().name("Viabilidad del curso").addClasspathResource(DMN);
        var proceso = motor.getRepositoryService()
                .createDeployment().name("Organización de cursos").addClasspathResource(BPMN);
        if (evitarDuplicados) {
            decisiones.enableDuplicateFiltering();
            proceso.enableDuplicateFiltering();
        }
        decisiones.deploy();
        return proceso.deploy().getId();
    }

    static void crearPersonal(IdmIdentityService identidad, String clave) {
        crearGrupo(identidad, TareasCurso.ADMINISTRACION, "Administración");
        crearGrupo(identidad, TareasCurso.DOCENTES, "Docentes");
        crearUsuario(identidad, "jefeAdmin", "Jefatura administrativa", TareasCurso.ADMINISTRACION, clave);
        crearUsuario(identidad, "adminCursos", "Administración de cursos", TareasCurso.ADMINISTRACION, clave);
        crearUsuario(identidad, "jefeDocente", "Jefatura docente", TareasCurso.DOCENTES, clave);
        crearUsuario(identidad, "docente1", "Docente 1", TareasCurso.DOCENTES, clave);
        crearUsuario(identidad, "docente2", "Docente 2", TareasCurso.DOCENTES, clave);
    }

    private static void crearGrupo(IdmIdentityService identidad, String id, String nombre) {
        if (identidad.createGroupQuery().groupId(id).count() == 0) {
            Group grupo = identidad.newGroup(id);
            grupo.setName(nombre);
            grupo.setType("assignment");
            identidad.saveGroup(grupo);
        }
        Privilege acceso = identidad.createPrivilegeQuery().privilegeName("access-task").singleResult();
        if (acceso != null && identidad.getPrivilegeMappingsByPrivilegeId(acceso.getId())
                .stream().noneMatch(mapeo -> id.equals(mapeo.getGroupId()))) {
            identidad.addGroupPrivilegeMapping(acceso.getId(), id);
        }
    }

    private static void crearUsuario(IdmIdentityService identidad, String id, String nombre, String grupo, String clave) {
        if (identidad.createUserQuery().userId(id).count() == 0) {
            User usuario = identidad.newUser(id);
            usuario.setFirstName(nombre);
            usuario.setPassword(clave);
            identidad.saveUser(usuario);
        }
        if (identidad.createUserQuery().userId(id).memberOfGroup(grupo).count() == 0) {
            identidad.createMembership(id, grupo);
        }
    }

    private static String requerirEntorno(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Definí la variable " + nombre);
        }
        return valor;
    }
}
