package proyecto.cursos.auxiliares;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import proyecto.cursos.MotorRest;

public final class AppComunicaciones {
    private final MotorRest motor;
    private final JFrame ventana = new JFrame("Simulador de comunicaciones de la empresa");
    private final JComboBox<String> solicitud = new JComboBox<>();
    private final JComboBox<String> mensaje = new JComboBox<>();
    private final JLabel campo = new JLabel("Datos");
    private final JTextField datos = new JTextField(35);
    private final JButton actualizar = new JButton("Actualizar solicitudes");
    private final JButton enviar = new JButton("Enviar comunicación");
    private final JLabel estado = new JLabel(" ");
    private Map<String, String> solicitudes = Map.of();
    private Map<String, String> mensajes = Map.of();
    private boolean ocupado;

    private AppComunicaciones(MotorRest motor) {
        this.motor = motor;
        var formulario = new JPanel(new GridLayout(3, 2, 8, 8));
        formulario.add(new JLabel("Solicitud"));
        formulario.add(solicitud);
        formulario.add(new JLabel("Comunicación"));
        formulario.add(mensaje);
        formulario.add(campo);
        formulario.add(datos);

        var botones = new JPanel();
        botones.add(actualizar);
        botones.add(enviar);
        var panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(formulario, BorderLayout.NORTH);
        panel.add(botones, BorderLayout.CENTER);
        panel.add(estado, BorderLayout.SOUTH);
        ventana.setContentPane(panel);
        ventana.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        actualizar.addActionListener(evento -> cargarSolicitudes());
        solicitud.addActionListener(evento -> {
            if (!ocupado) cargarMensajes();
        });
        mensaje.addActionListener(evento -> {
            datos.setText("");
            habilitarControles();
        });
        enviar.addActionListener(evento -> enviar());

        ventana.pack();
        ventana.setLocationByPlatform(true);
        ventana.setVisible(true);
        cargarSolicitudes();
    }

    public static void main(String[] args) {
        MotorRest motor = SimularComunicaciones.conectar();
        SwingUtilities.invokeLater(() -> new AppComunicaciones(motor));
    }

    private void cargarSolicitudes() {
        ejecutar(() -> SimularComunicaciones.solicitudes(motor), resultado -> {
            solicitudes = resultado;
            solicitud.setModel(new DefaultComboBoxModel<>(solicitudes.keySet().toArray(String[]::new)));
            mensajes = Map.of();
            mensaje.removeAllItems();
            estado.setText(solicitudes.isEmpty() ? "No hay solicitudes activas." : "Elegí una solicitud.");
            SwingUtilities.invokeLater(this::cargarMensajes);
        });
    }

    private void cargarMensajes() {
        String etiqueta = (String) solicitud.getSelectedItem();
        if (etiqueta == null) return;
        String id = solicitudes.get(etiqueta);
        mensajes = Map.of();
        mensaje.removeAllItems();
        ejecutar(() -> SimularComunicaciones.disponibles(motor, id), resultado -> {
            mensajes = resultado;
            mensaje.setModel(new DefaultComboBoxModel<>(mensajes.keySet().toArray(String[]::new)));
            estado.setText(mensajes.isEmpty() ? "La solicitud todavía no espera una comunicación." : "Elegí una comunicación.");
        });
    }

    private void enviar() {
        String nombre = (String) mensaje.getSelectedItem();
        String ejecucion = mensajes.get(nombre);
        String valor = datos.getText();
        ejecutar(() -> {
            SimularComunicaciones.enviar(motor, ejecucion, nombre, valor);
            return nombre;
        }, resultado -> {
            mensaje.removeAllItems();
            mensajes = Map.of();
            JOptionPane.showMessageDialog(ventana, "Comunicación enviada: " + resultado);
            SwingUtilities.invokeLater(this::cargarSolicitudes);
        });
    }

    private void habilitarControles() {
        String tipo = (String) mensaje.getSelectedItem();
        String etiqueta = tipo == null ? null : SimularComunicaciones.campo(tipo);
        campo.setText(etiqueta == null ? "Datos" : etiqueta);
        datos.setEnabled(!ocupado && etiqueta != null);
        actualizar.setEnabled(!ocupado);
        solicitud.setEnabled(!ocupado && !solicitudes.isEmpty());
        mensaje.setEnabled(!ocupado && !mensajes.isEmpty());
        enviar.setEnabled(!ocupado && tipo != null);
    }

    private <T> void ejecutar(Callable<T> operacion, Consumer<T> mostrar) {
        ocupado = true;
        habilitarControles();
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return operacion.call();
            }

            @Override
            protected void done() {
                try {
                    mostrar.accept(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    estado.setText("Error: " + ex.getCause().getMessage());
                } finally {
                    ocupado = false;
                    habilitarControles();
                }
            }
        }.execute();
    }
}
