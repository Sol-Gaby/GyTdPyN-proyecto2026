package proyecto.cursos.auxiliares;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import org.flowable.engine.ProcessEngine;

public final class AppMateriales extends JFrame {
    private final ServicioMateriales servicio;
    private final JTextField usuario = new JTextField(12);
    private final JPasswordField clave = new JPasswordField(12);
    private final JButton iniciar = new JButton("Ingresar");
    private final JButton salir = new JButton("Cerrar sesión");
    private final JButton consultar = new JButton("Actualizar");
    private final JButton reclamar = new JButton("Reclamar");
    private final JButton completar = new JButton("Completar");
    private final JLabel estado = new JLabel("Ingresá con tu usuario de Flowable.");
    private final ModeloTareas modelo = new ModeloTareas();
    private final JTable tabla = new JTable(modelo);
    private String usuarioIdentificado;
    private boolean trabajando;

    public static void main(String[] argumentos) {
        try {
            ProcessEngine motor = ConexionMotor.abrir();
            SwingUtilities.invokeLater(() -> new AppMateriales(motor).setVisible(true));
        } catch (RuntimeException error) {
            JOptionPane.showMessageDialog(null, error.getMessage(), "Conexión con Flowable", JOptionPane.ERROR_MESSAGE);
        }
    }

    private AppMateriales(ProcessEngine motor) {
        super("Preparación de materiales");
        servicio = new ServicioMateriales(motor);
        JPanel ingreso = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ingreso.add(new JLabel("Usuario"));
        ingreso.add(usuario);
        ingreso.add(new JLabel("Contraseña"));
        ingreso.add(clave);
        ingreso.add(iniciar);
        ingreso.add(salir);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.add(consultar);
        acciones.add(reclamar);
        acciones.add(completar);
        acciones.add(estado);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(ingreso, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(acciones, BorderLayout.SOUTH);

        iniciar.addActionListener(evento -> ingresar());
        salir.addActionListener(evento -> {
            usuarioIdentificado = null;
            mostrar(List.of(), "Sesión cerrada.");
            usuario.requestFocusInWindow();
            ajustarBotones();
        });
        consultar.addActionListener(evento -> cargar());
        reclamar.addActionListener(evento -> modificar(false));
        completar.addActionListener(evento -> modificar(true));
        tabla.getSelectionModel().addListSelectionListener(evento -> ajustarBotones());
        getRootPane().setDefaultButton(iniciar);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                if (!trabajando) {
                    motor.close();
                    dispose();
                }
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(800, 380);
        setLocationByPlatform(true);
        ajustarBotones();
    }

    private void ingresar() {
        String id = usuario.getText().strip();
        char[] ingresada = clave.getPassword();
        String contraseña = new String(ingresada);
        Arrays.fill(ingresada, '\0');
        clave.setText("");
        if (id.isEmpty() || contraseña.isEmpty()) {
            estado.setText("Completá usuario y contraseña.");
            return;
        }
        ejecutar(() -> {
            servicio.identificar(id, contraseña);
            return servicio.consultar(id);
        }, filas -> {
            usuarioIdentificado = id;
            mostrar(filas, "Sesión de " + id + ".");
        }, false);
    }

    private void cargar() {
        String id = usuarioIdentificado;
        ejecutar(() -> servicio.consultar(id), filas -> mostrar(filas, "Tareas actualizadas."), false);
    }

    private void modificar(boolean finalizar) {
        var fila = seleccionada();
        if (fila == null) {
            return;
        }
        String id = usuarioIdentificado;
        // Una modificación y la recarga son pasos separados. Si la recarga falla, el aviso ya registra el éxito.
        ejecutar(() -> {
            if (finalizar) {
                servicio.completar(id, fila.id());
            } else {
                servicio.reclamar(id, fila.id());
            }
            return null;
        }, ignorado -> {
            mostrar(List.of(), finalizar ? "Tarea completada." : "Tarea reclamada.");
            cargarDespuesDeCambio();
        }, true);
    }

    private void cargarDespuesDeCambio() {
        String id = usuarioIdentificado;
        String mensaje = estado.getText();
        ejecutar(() -> servicio.consultar(id), filas -> mostrar(filas, mensaje), false);
    }

    private <T> void ejecutar(Callable<T> accion, Consumer<T> alTerminar, boolean cambio) {
        trabajando = true;
        ajustarBotones();
        // SwingWorker deja la consulta fuera del hilo que dibuja la interfaz.
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return accion.call();
            }

            @Override
            protected void done() {
                trabajando = false;
                try {
                    alTerminar.accept(get());
                } catch (ExecutionException error) {
                    if (cambio) {
                        mostrar(List.of(), "Actualizá las tareas antes de reintentar.");
                    }
                    JOptionPane.showMessageDialog(AppMateriales.this, error.getCause().getMessage(),
                            "Operación de Flowable", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    estado.setText("Operación interrumpida. Actualizá la lista.");
                } finally {
                    ajustarBotones();
                }
            }
        }.execute();
    }

    private ServicioMateriales.Fila seleccionada() {
        int indice = tabla.getSelectedRow();
        return indice < 0 ? null : modelo.filas.get(tabla.convertRowIndexToModel(indice));
    }

    private void mostrar(List<ServicioMateriales.Fila> filas, String mensaje) {
        tabla.clearSelection();
        modelo.filas = filas;
        modelo.fireTableDataChanged();
        estado.setText(mensaje);
    }

    private void ajustarBotones() {
        boolean conSesion = usuarioIdentificado != null;
        var fila = seleccionada();
        usuario.setEnabled(!trabajando && !conSesion);
        clave.setEnabled(!trabajando && !conSesion);
        iniciar.setEnabled(!trabajando && !conSesion);
        salir.setEnabled(!trabajando && conSesion);
        consultar.setEnabled(!trabajando && conSesion);
        reclamar.setEnabled(!trabajando && conSesion && fila != null && !fila.asignada());
        completar.setEnabled(!trabajando && conSesion && fila != null && fila.asignada());
    }

    private static final class ModeloTareas extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Tarea", "Instancia", "Actividad", "Disponibilidad"};
        private List<ServicioMateriales.Fila> filas = List.of();

        @Override public int getRowCount() { return filas.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int indice, int columna) {
            var fila = filas.get(indice);
            switch (columna) {
                case 0: return fila.id();
                case 1: return fila.instancia();
                case 2: return fila.nombre();
                case 3: return fila.asignada() ? "Asignada a mí" : "Disponible para reclamar";
                default: throw new IndexOutOfBoundsException(columna);
            }
        }
    }
}
