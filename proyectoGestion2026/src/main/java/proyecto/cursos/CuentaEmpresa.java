package proyecto.cursos;

/** Simula la entrega de credenciales a una empresa que ya aceptó una propuesta. */
public final class CuentaEmpresa {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || System.console() == null)
            throw new IllegalArgumentException("Usá CuentaEmpresa CODIGO desde una terminal interactiva");
        char[] clave = System.console().readPassword("Clave nueva para empresa-%s: ", args[0]);
        if (clave == null) throw new IllegalStateException("No se ingresó una clave");
        try {
            new Institucion(System.getenv("CURSOS_INSTITUCION_JDBC_URL"),
                    System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"))
                    .establecerClave(args[0], new String(clave));
            System.out.println("Credenciales disponibles para empresa-" + args[0]);
        } finally {
            java.util.Arrays.fill(clave, '\0');
        }
    }
}
