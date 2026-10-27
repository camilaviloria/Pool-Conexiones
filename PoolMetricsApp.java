import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class PoolMetricsApp extends Application {

    private PoolManager manager;
    private AnimationTimer timer; // Guardamos el timer para poder detenerlo

    private final Label lblVelocidad  = new Label();
    private final Label lblFallos     = new Label();
    private final Label lblTasaFallos = new Label();
    private final Label lblOps        = new Label();
    private final Label lblEnUso      = new Label();
    private final Label lblEnEspera   = new Label();
    private final Label lblLibres     = new Label();
    private final Label lblCreadas    = new Label();
    
    private final Button btnDetener   = new Button("Detener Simulación");

    @Override
    public void start(Stage stage) throws Exception {
        // 1. Configurar el Pool
        SimuladorConexion factory = new SimuladorConexion();
        ConnectionPool pool = new ConnectionPool(10, 50, factory); // 10 iniciales, 50 máximo
        
        // 2. Iniciar 500 hilos concurrentes
        manager = new PoolManager(pool, 500);
        manager.iniciar();

        // 3. Configurar la interfaz gráfica
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(20);
        grid.setVgap(10);

        int fila = 0;
        grid.add(new Label("Velocidad promedio:"), 0, fila); grid.add(lblVelocidad,  1, fila++);
        grid.add(new Label("Operaciones totales:"), 0, fila); grid.add(lblOps,        1, fila++);
        grid.add(new Label("Fallos:"),             0, fila); grid.add(lblFallos,     1, fila++);
        grid.add(new Label("Tasa de fallos:"),     0, fila); grid.add(lblTasaFallos, 1, fila++);
        grid.add(new Label("Conexiones en uso:"),  0, fila); grid.add(lblEnUso,      1, fila++);
        grid.add(new Label("Hilos en espera:"),    0, fila); grid.add(lblEnEspera,   1, fila++);
        grid.add(new Label("Conexiones libres:"),  0, fila); grid.add(lblLibres,     1, fila++);
        grid.add(new Label("Conexiones creadas:"), 0, fila); grid.add(lblCreadas,    1, fila++);

        // Agregamos el botón al final
        grid.add(btnDetener, 0, fila, 2, 1); 

        stage.setScene(new Scene(grid, 360, 330));
        stage.setTitle("Pool Manager — Métricas en Tiempo Real");
        stage.show();

        // 4. Timer para refrescar los datos
        timer = new AnimationTimer() {
            private long ultimo = 0;
            @Override
            public void handle(long ahora) {
                if (ahora - ultimo < 300_000_000L) return;
                ultimo = ahora;
                Platform.runLater(PoolMetricsApp.this::refrescar);
            }
        };
        timer.start(); // Iniciamos la actualización

        // 5. Acción del botón para detener todo
        btnDetener.setOnAction((ActionEvent e) -> {
            timer.stop();      // Congela las métricas en pantalla
            manager.detener(); // Detiene los 500 hilos en segundo plano
            btnDetener.setDisable(true);
            btnDetener.setText("Simulación Detenida");
        });
    }

    private void refrescar() {
        lblVelocidad.setText(String.format("%.1f ms", manager.getVelocidadPromedioMs()));
        lblOps.setText(String.valueOf(manager.getOperaciones()));
        lblFallos.setText(String.valueOf(manager.getFallos()));
        lblTasaFallos.setText(String.format("%.1f %%", manager.getTasaFallosPorciento()));
        lblEnUso.setText(manager.getEnUso() + " / " + manager.getCapacidad());
        lblEnEspera.setText(String.valueOf(manager.getEnEspera()));
        lblLibres.setText(String.valueOf(manager.getLibres()));
        lblCreadas.setText(String.valueOf(manager.getCreadas()));
    }

    @Override
    public void stop() {
        // Esto se ejecuta si el usuario cierra la ventana con la "X"
        if (manager != null) manager.detener();
        if (timer != null) timer.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}