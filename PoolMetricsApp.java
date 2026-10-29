import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PoolMetricsApp extends Application {

    private PoolManager manager;
    private AnimationTimer timer;

    // Etiquetas de texto
    private final Label lblVelocidad  = new Label();
    private final Label lblFallos     = new Label();
    private final Label lblTasaFallos = new Label();
    private final Label lblOps        = new Label();
    private final Label lblEnUso      = new Label();
    private final Label lblEnEspera   = new Label();
    private final Label lblLibres     = new Label();
    private final Label lblCreadas    = new Label();
    private final Button btnDetener   = new Button("Detener Simulación");

    // Series para la gráfica
    private XYChart.Series<Number, Number> serieEnUso;
    private XYChart.Series<Number, Number> serieEnEspera;
    private int segundoActual = 0; // Eje X de la gráfica

    @Override
    public void start(Stage stage) throws Exception {
        // 1. Configurar el Pool
        CreadorConexiones factory = new SimuladorConexion();
        ConnectionPool pool = new ConnectionPool(10, 50, factory);
        
        // 2. Obtener la instancia del Singleton (PoolManager)
        manager = PoolManager.getInstance(pool, 500);
        manager.iniciar();

        // 3. Configurar la interfaz gráfica (Textos)
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(15);
        grid.setVgap(8);

        int fila = 0;
        grid.add(new Label("Velocidad:"), 0, fila); grid.add(lblVelocidad,  1, fila++);
        grid.add(new Label("Operaciones:"), 0, fila); grid.add(lblOps,        1, fila++);
        grid.add(new Label("Fallos:"), 0, fila); grid.add(lblFallos,     1, fila++);
        grid.add(new Label("Tasa fallos:"), 0, fila); grid.add(lblTasaFallos, 1, fila++);
        grid.add(new Label("En uso:"), 0, fila); grid.add(lblEnUso,      1, fila++);
        grid.add(new Label("En espera:"), 0, fila); grid.add(lblEnEspera,   1, fila++);
        grid.add(new Label("Libres:"), 0, fila); grid.add(lblLibres,     1, fila++);
        grid.add(new Label("Creadas:"), 0, fila); grid.add(lblCreadas,    1, fila++);
        grid.add(btnDetener, 0, fila, 2, 1); 

        // 4. Configurar la Gráfica (Eje X: Tiempo, Eje Y: Cantidad)
        NumberAxis ejeX = new NumberAxis();
        ejeX.setLabel("Tiempo (Actualizaciones)");
        ejeX.setForceZeroInRange(false);
        
        NumberAxis ejeY = new NumberAxis();
        ejeY.setLabel("Cantidad");
        
        LineChart<Number, Number> lineChart = new LineChart<>(ejeX, ejeY);
        lineChart.setTitle("Concurrencia del Pool");
        lineChart.setCreateSymbols(false); // Quita los puntos para que se vea como una línea continua
        lineChart.setAnimated(false); // Desactiva la animación para mejor rendimiento

        // Creamos las series de datos
        serieEnUso = new XYChart.Series<>();
        serieEnUso.setName("Conexiones en Uso");
        
        serieEnEspera = new XYChart.Series<>();
        serieEnEspera.setName("Hilos en Espera");

        lineChart.getData().add(serieEnEspera); // Agregamos primero la de espera
        lineChart.getData().add(serieEnUso);

        // 5. Unir todo en la ventana
        VBox root = new VBox(10, grid, lineChart);
        root.setPadding(new Insets(10));

        stage.setScene(new Scene(root, 600, 550)); // Ventana más grande para que quepa la gráfica
        stage.setTitle("Pool Manager — Métricas y Gráfica");
        stage.show();

        // 6. Timer para actualizar datos y gráfica
        timer = new AnimationTimer() {
            private long ultimo = 0;
            @Override
            public void handle(long ahora) {
                // Se actualiza cada 500 milisegundos para que la gráfica no se sature
                if (ahora - ultimo < 500_000_000L) return;
                ultimo = ahora;
                Platform.runLater(PoolMetricsApp.this::refrescar);
            }
        };
        timer.start();

        // 7. Acción del botón
        btnDetener.setOnAction((ActionEvent e) -> {
            timer.stop();
            manager.detener();
            btnDetener.setDisable(true);
            btnDetener.setText("Simulación Detenida");
        });
    }

    private void refrescar() {
        // Actualizar textos
        lblVelocidad.setText(String.format("%.1f ms", manager.getVelocidadPromedioMs()));
        lblOps.setText(String.valueOf(manager.getOperaciones()));
        lblFallos.setText(String.valueOf(manager.getFallos()));
        lblTasaFallos.setText(String.format("%.1f %%", manager.getTasaFallosPorciento()));
        lblEnUso.setText(manager.getEnUso() + " / " + manager.getCapacidad());
        lblEnEspera.setText(String.valueOf(manager.getEnEspera()));
        lblLibres.setText(String.valueOf(manager.getLibres()));
        lblCreadas.setText(String.valueOf(manager.getCreadas()));

        // Actualizar gráfica
        segundoActual++;
        
        // Si la gráfica tiene muchos puntos (ej. más de 30), borramos el más viejo para que no se comprima
        if (serieEnUso.getData().size() > 30) {
            serieEnUso.getData().remove(0);
            serieEnEspera.getData().remove(0);
        }

        // Agregar los nuevos puntos a la gráfica
        serieEnUso.getData().add(new XYChart.Data<>(segundoActual, manager.getEnUso()));
        serieEnEspera.getData().add(new XYChart.Data<>(segundoActual, manager.getEnEspera()));
    }

    @Override
    public void stop() {
        if (manager != null) manager.detener();
        if (timer != null) timer.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}