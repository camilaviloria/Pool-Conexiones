import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
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

    private final Label lblVelocidad  = new Label();
    private final Label lblTasaFallos = new Label();
    private final Label lblEnUso      = new Label();
    private final Label lblEnEspera   = new Label();
    private final Label lblCreadas    = new Label();
    
    private final Label lblSinPoolConexiones = new Label("10,000 (Colapso de red)");
    private final Label lblSinPoolVelocidad  = new Label("~500,000 ms (8.3 min)");

    private final Button btnDetener = new Button("Detener y Mostrar SIN POOL");

    private XYChart.Series<Number, Number> serieEnUso;
    private XYChart.Series<Number, Number> serieEnEspera;
    private XYChart.Series<String, Number> serieComparacion;
    private int segundoActual = 0;

    @Override
    public void start(Stage stage) throws Exception {
        CreadorConexiones factory = new SimuladorConexion();
        ConnectionPool pool = new ConnectionPool(20, 300, factory);
        manager = PoolManager.getInstance(pool, 10000);
        manager.iniciar();

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(15);
        grid.setVgap(8);

        int fila = 0;
        grid.add(new Label("CON POOL -> Velocidad:"), 0, fila); grid.add(lblVelocidad, 1, fila++);
        grid.add(new Label("CON POOL -> Tasa fallos:"), 0, fila); grid.add(lblTasaFallos, 1, fila++);
        grid.add(new Label("CON POOL -> En uso:"), 0, fila); grid.add(lblEnUso, 1, fila++);
        grid.add(new Label("CON POOL -> En espera:"), 0, fila); grid.add(lblEnEspera, 1, fila++);
        grid.add(new Label("CON POOL -> Creadas:"), 0, fila); grid.add(lblCreadas, 1, fila++);
        
        grid.add(new Label("SIN POOL -> Conexiones:"), 0, fila); grid.add(lblSinPoolConexiones, 1, fila++);
        grid.add(new Label("SIN POOL -> Tiempo:"), 0, fila); grid.add(lblSinPoolVelocidad, 1, fila++);
        grid.add(btnDetener, 0, fila, 2, 1); 

        NumberAxis ejeX = new NumberAxis();
        NumberAxis ejeY = new NumberAxis();
        LineChart<Number, Number> lineChart = new LineChart<>(ejeX, ejeY);
        lineChart.setTitle("Concurrencia CON POOL (10,000 hilos)");
        lineChart.setCreateSymbols(false);
        lineChart.setAnimated(false);

        serieEnUso = new XYChart.Series<>();
        serieEnUso.setName("Conexiones en Uso");
        serieEnEspera = new XYChart.Series<>();
        serieEnEspera.setName("Hilos en Espera");
        lineChart.getData().addAll(serieEnEspera, serieEnUso);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Tiempo de Respuesta Promedio (ms)");
        barChart.setAnimated(false);
        
        serieComparacion = new XYChart.Series<>();
        serieComparacion.setName("Latencia");
        serieComparacion.getData().add(new XYChart.Data<>("Con Pool", 105)); 
        serieComparacion.getData().add(new XYChart.Data<>("Sin Pool (10k)", 500000)); 
        barChart.getData().add(serieComparacion);

        VBox root = new VBox(10, grid, lineChart, barChart);
        root.setPadding(new Insets(10));

        stage.setScene(new Scene(root, 650, 750));
        stage.setTitle("Comparativa: 10,000 Hilos");
        stage.show();

        timer = new AnimationTimer() {
            private long ultimo = 0;
            @Override
            public void handle(long ahora) {
                if (ahora - ultimo < 1_000_000_000L) return;
                ultimo = ahora;
                Platform.runLater(PoolMetricsApp.this::refrescar);
            }
        };
        timer.start();

        btnDetener.setOnAction(e -> {
            timer.stop();
            manager.detener();
            btnDetener.setDisable(true);
            btnDetener.setText("Simulación Finalizada");
            
            double tiempoRealConPool = manager.getVelocidadPromedioMs();
            serieComparacion.getData().get(0).setYValue(tiempoRealConPool);
        });
    }

    private void refrescar() {
        lblVelocidad.setText(String.format("%.1f ms", manager.getVelocidadPromedioMs()));
        lblTasaFallos.setText(String.format("%.1f %%", manager.getTasaFallosPorciento()));
        lblEnUso.setText(manager.getEnUso() + " / " + manager.getCapacidad());
        lblEnEspera.setText(String.valueOf(manager.getEnEspera()));
        lblCreadas.setText(String.valueOf(manager.getCreadas()));

        segundoActual++;
        if (serieEnUso.getData().size() > 20) {
            serieEnUso.getData().remove(0);
            serieEnEspera.getData().remove(0);
        }
        serieEnUso.getData().add(new XYChart.Data<>(segundoActual, manager.getEnUso()));
        serieEnEspera.getData().add(new XYChart.Data<>(segundoActual, manager.getEnEspera()));
    }

    @Override
    public void stop() {
        if (manager != null) manager.detener();
        if (timer != null) timer.stop();
    }

    public static void main(String[] args) { launch(args); }
}