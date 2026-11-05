package com.pool;

import com.pool.db.DatabaseType;
import com.pool.metrics.Metrics;
import com.pool.metrics.MetricsSnapshot;
import com.pool.sim.Simulation;
import com.pool.sim.SimulationSettings;
import com.pool.util.AppConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class PoolMetricsApp extends Application {

    private final ToggleGroup engineGroup = new ToggleGroup();
    private final Button btnEjecutar = new Button("▶  EJECUTAR");

    private final Label lblVelPool    = new Label("—");
    private final Label lblVelDirect  = new Label("—");
    private final Label lblDelta      = new Label("—");
    private final Label lblFallos     = new Label("—");

    private final Label lblTiempoPool   = new Label("—");
    private final Label lblTiempoDirect = new Label("—");
    private final Label lblGanador      = new Label("—");
    private final Label lblDeltaPool    = new Label("");
    private final Label lblDeltaDirect  = new Label("");
    private final Label lblPorcentaje   = new Label("");

    private XYChart.Series<Number, Number> seriePool;
    private XYChart.Series<Number, Number> serieDirect;

    private final TableView<Metrica> tabla = new TableView<>();
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label("Listo.");

    // Spinners
    private final Spinner<Integer> spRequests = sp(1000, 1, 200_000, 500);
    private final Spinner<Integer> spThreads  = sp(200, 1, 5000, 10);
    private final Spinner<Integer> spRetries  = sp(2, 0, 999, 1);
    private final Spinner<Integer> spPoolInit = sp(AppConfig.getInt("pool.init.size", 20), 0, 5000, 5);
    private final Spinner<Integer> spPoolMax  = sp(AppConfig.getInt("pool.max.size", 200), 1, 5000, 5);

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(buildTopBar());
        root.setLeft(buildSidebar());
        root.setCenter(buildCentro());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1150, 780);

        var cssUrl = getClass().getResource("/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("No se encontró styles.css en resources.");
        }

        stage.setScene(scene);
        stage.setTitle("Pool vs Sin Pool — Comparador");
        stage.show();

        btnEjecutar.setOnAction(e -> ejecutarComparacion());
    }

    private HBox buildTopBar() {
        Label title = new Label("⚡ Pool vs Sin Pool");
        title.getStyleClass().add("title");

        ToggleButton pg  = pill("PostgreSQL");
        ToggleButton my  = pill("MySQL");
        ToggleButton sim = pill("Simulado");
        sim.setSelected(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox params = new HBox(8,
                label("Peticiones:"), spRequests,
                label("Hilos:"), spThreads,
                label("Reintentos:"), spRetries,
                label("Pool ini:"), spPoolInit,
                label("Pool máx:"), spPoolMax);
        params.setAlignment(Pos.CENTER_LEFT);

        btnEjecutar.getStyleClass().add("btn-ejecutar");
        btnEjecutar.setMaxHeight(Double.MAX_VALUE);

        HBox bar = new HBox(14, title, spacer, pg, my, sim, params, btnEjecutar);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private ToggleButton pill(String text) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(engineGroup);
        b.getStyleClass().add("pill");
        return b;
    }

    private Label label(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("field-label");
        return l;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(12,
                kpiCard("CON POOL — Latencia",     lblVelPool,   "pool"),
                kpiCard("SIN POOL — Latencia",     lblVelDirect, "direct"),
                kpiCard("Δ Latencia promedio",     lblDelta,     ""),
                kpiCard("Fallos (pool / directo)", lblFallos,    "")
        );
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private VBox kpiCard(String title, Label valueLabel, String modifier) {
        Label t = new Label(title);
        t.getStyleClass().add("kpi-title");
        valueLabel.getStyleClass().add("kpi-value");
        VBox card = new VBox(4, t, valueLabel);
        card.getStyleClass().add("kpi-card");
        if (!modifier.isEmpty()) card.getStyleClass().add(modifier);
        return card;
    }

    private VBox buildCentro() {
        HBox tarjetas = new HBox(12,
                compareCard("CON POOL",     lblTiempoPool,   lblDeltaPool),
                compareCard("SIN POOL",     lblTiempoDirect, lblDeltaDirect),
                compareCard("MÁS RÁPIDO",   lblGanador,      lblPorcentaje)
        );
        HBox.setHgrow(tarjetas, Priority.ALWAYS);

        LineChart<Number, Number> chart = lineChartProgreso();
        VBox.setVgrow(chart, Priority.ALWAYS);

        configurarTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox centro = new VBox(14, tarjetas, chart, tabla);
        centro.getStyleClass().add("center-zone");
        return centro;
    }

    private VBox compareCard(String title, Label value, Label delta) {
        Label t = new Label(title);
        t.getStyleClass().add("label-title");
        value.getStyleClass().add("label-value");
        delta.getStyleClass().add("label-delta");
        VBox card = new VBox(4, t, value, delta);
        card.getStyleClass().add("compare-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private LineChart<Number, Number> lineChartProgreso() {
        NumberAxis x = new NumberAxis();
        NumberAxis y = new NumberAxis();
        x.setLabel("Segundo");
        y.setLabel("Peticiones completadas / s");

        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle("Progreso durante la comparación");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);

        seriePool = new XYChart.Series<>();
        seriePool.setName("CON pool");
        serieDirect = new XYChart.Series<>();
        serieDirect.setName("SIN pool");
        chart.getData().add(seriePool);
        chart.getData().add(serieDirect);
        
        Platform.runLater(() -> {
            if (seriePool.getNode() != null)
                seriePool.getNode().setStyle("-fx-stroke: #4CAF50; -fx-stroke-width: 2.5;");
            if (serieDirect.getNode() != null)
                serieDirect.getNode().setStyle("-fx-stroke: #FFB300; -fx-stroke-width: 2.5;");
        });

        return chart;
    }

    private void configurarTabla() {
        TableColumn<Metrica, String> c1 = new TableColumn<>("Métrica");
        c1.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nombre()));
        c1.setPrefWidth(220);

        TableColumn<Metrica, String> c2 = new TableColumn<>("CON POOL");
        c2.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().conPool()));

        TableColumn<Metrica, String> c3 = new TableColumn<>("SIN POOL");
        c3.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().sinPool()));

        TableColumn<Metrica, String> c4 = new TableColumn<>("Mejor");
        c4.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().mejor()));
        c4.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                switch (item) {
                    case "CON POOL" -> setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                    case "SIN POOL" -> setStyle("-fx-text-fill: #FFB300; -fx-font-weight: bold;");
                    default        -> setStyle("-fx-text-fill: #8A8D9A;");
                }
            }
        });

        tabla.getColumns().addAll(c1, c2, c3, c4);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private HBox buildStatusBar() {
        status.getStyleClass().add("status-text");
        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progress, Priority.ALWAYS);
        HBox bar = new HBox(12, status, progress);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void ejecutarComparacion() {
        btnEjecutar.setDisable(true);
        tabla.getItems().clear();
        seriePool.getData().clear();
        serieDirect.getData().clear();

        // Si el usuario escribió en el Spinner pero no le dio Enter, forzamos el commit
        try {
            spRequests.commitValue();
            spThreads.commitValue();
            spRetries.commitValue();
            spPoolInit.commitValue();
            spPoolMax.commitValue();
        } catch (Exception ignored) {}

        int requests = spRequests.getValue();
        SimulationSettings settings = new SimulationSettings(
                requests, spThreads.getValue(), spRetries.getValue(),
                spPoolInit.getValue(), spPoolMax.getValue()
        );

        ToggleButton selected = (ToggleButton) engineGroup.getSelectedToggle();
        DatabaseType engine = selected != null && selected.getText().equals("MySQL") ? DatabaseType.MYSQL
                            : selected != null && selected.getText().equals("PostgreSQL") ? DatabaseType.POSTGRES
                            : DatabaseType.SIMULADO;

        Thread hilo = new Thread(() -> {
            try {
                // 1. CON POOL
                Platform.runLater(() -> status.setText("Ejecutando CON POOL..."));
                Simulation sim = new Simulation(engine.createFactory(), settings);
                Metrics m1 = new Metrics();
                MetricsSnapshot snapPool = sim.run(Simulation.Mode.WITH_POOL, m1, 0);

                // 2. SIN POOL
                Platform.runLater(() -> status.setText("Ejecutando SIN POOL (máx 15s)..."));
                Metrics m2 = new Metrics();
                MetricsSnapshot snapDirect = sim.run(
                        Simulation.Mode.WITHOUT_POOL, m2,
                        Simulation.TIMEOUT_SIN_POOL_MS
                );

                // 3. Mostrar resultados
                Platform.runLater(() -> {
                    mostrarResultados(snapPool, snapDirect);
                    
                    if (snapDirect.isAborted()) {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Prueba abortada");
                        alert.setHeaderText("⚠️ La prueba SIN POOL superó los 15 segundos");
                        alert.setContentText(
                                "Se abortó la prueba SIN POOL para no exceder el límite de 15s.\n\n" +
                                "Se completaron " + snapDirect.getRequests() + " de " + requests + " peticiones.\n" +
                                "Los resultados mostrados son parciales."
                        );
                        alert.showAndWait();
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
                ex.printStackTrace();
            } finally {
                Platform.runLater(() -> {
                    progress.setProgress(0);
                    btnEjecutar.setDisable(false);
                    status.setText("Terminado.");
                });
            }
        });
        hilo.start();
    }

    private void mostrarResultados(MetricsSnapshot pool, MetricsSnapshot direct) {
        lblTiempoPool.setText(pool.getElapsedMs() + " ms");
        lblVelPool.setText(String.format("%.1f ms", pool.getAvgResponseMs()));
        lblVelDirect.setText(String.format("%.1f ms", direct.getAvgResponseMs()));
        lblFallos.setText(String.format("%.1f%% / %.1f%%", pool.getFailurePercent(), direct.getFailurePercent()));

        double delta = pool.getAvgResponseMs() - direct.getAvgResponseMs();
        lblDelta.setText(String.format("%+.1f ms", delta));

        boolean poolGana = pool.getElapsedMs() <= direct.getElapsedMs();
        lblGanador.setText(poolGana ? "CON POOL" : "SIN POOL");
        
        long lento = Math.max(pool.getElapsedMs(), direct.getElapsedMs());
        long rapido = Math.min(pool.getElapsedMs(), direct.getElapsedMs());
        lblPorcentaje.setText(lento == 0 ? "—" : String.format("%.1f %% menos tiempo", (lento - rapido) * 100.0 / lento));

        if (direct.isAborted()) {
            lblTiempoDirect.setText(direct.getElapsedMs() + " ms (abortado)");
            lblTiempoDirect.setStyle("-fx-text-fill: #E53935;"); 
        } else {
            lblTiempoDirect.setText(direct.getElapsedMs() + " ms");
            lblTiempoDirect.setStyle(""); 
        }

        // Gráfica
        seriePool.getData().clear();
        serieDirect.getData().clear();
        seriePool.getData().add(new XYChart.Data<>(0, 0));
        serieDirect.getData().add(new XYChart.Data<>(0, 0));
        
        double tPool = pool.getThroughput();
        double tDirect = direct.getThroughput();
        for (int i = 1; i <= 10; i++) {
            seriePool.getData().add(new XYChart.Data<>(i, tPool));
            serieDirect.getData().add(new XYChart.Data<>(i, tDirect));
        }

        // Tabla
        tabla.getItems().setAll(
            // 🔥 NUEVA FILA CON EL TOTAL DE PETICIONES REALIZADAS
            new Metrica("Total Peticiones Realizadas", 
                        String.valueOf(pool.getRequests()), 
                        String.valueOf(direct.getRequests()), 
                        "—"),

            new Metrica("Tiempo total",         pool.getElapsedMs() + " ms",  direct.getElapsedMs() + " ms",
                        pool.getElapsedMs() <= direct.getElapsedMs() ? "CON POOL" : "SIN POOL"),
            new Metrica("Respuesta promedio",   String.format("%.1f ms", pool.getAvgResponseMs()),
                                                String.format("%.1f ms", direct.getAvgResponseMs()),
                        pool.getAvgResponseMs() <= direct.getAvgResponseMs() ? "CON POOL" : "SIN POOL"),
            new Metrica("Peticiones / segundo", String.format("%.1f", pool.getThroughput()),
                                                String.format("%.1f", direct.getThroughput()),
                        pool.getThroughput() >= direct.getThroughput() ? "CON POOL" : "SIN POOL"),
            new Metrica("Exitosas",             String.format("%.1f %%", pool.getSuccessPercent()),
                                                String.format("%.1f %%", direct.getSuccessPercent()),
                        pool.getSuccessPercent() >= direct.getSuccessPercent() ? "CON POOL" : "SIN POOL"),
            new Metrica("Fallidas",             String.valueOf(pool.getFailures()),
                                                String.valueOf(direct.getFailures()),
                        pool.getFailures() <= direct.getFailures() ? "CON POOL" : "SIN POOL"),
            new Metrica("Reintentos",           String.valueOf(pool.getRetries()),
                                                String.valueOf(direct.getRetries()), "—")
        );
    }

    private static Spinner<Integer> sp(int val, int min, int max, int step) {
        Spinner<Integer> s = new Spinner<>();
        s.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, val, step));
        s.setPrefWidth(85);
        s.setEditable(true); // 🔥 Permite escribir directamente en el cuadro
        return s;
    }

    public record Metrica(String nombre, String conPool, String sinPool, String mejor) {}

    public static void main(String[] args) {
        launch(args);
    }
}