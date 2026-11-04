package com.pool.sim;

import com.pool.cliente.Cliente;
import com.pool.cliente.ConnectionProvider;
import com.pool.cliente.DirectConnectionProvider;
import com.pool.cliente.PoolConnectionProvider;
import com.pool.metrics.Metrics;
import com.pool.metrics.MetricsSnapshot;
import com.pool.pool.ConnectionPool;
import com.pool.pool.CreadorConexiones;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Simulation {

    public enum Mode { WITH_POOL, WITHOUT_POOL }

    private static final long TIMEOUT_MS = 30_000;
    private static final long MAX_MINUTOS = 10;

    // 👇 Constante del tiempo máximo para el modo sin pool
    public static final long TIMEOUT_SIN_POOL_MS = 15_000;

    private final CreadorConexiones factory;
    private final SimulationSettings settings;

    public Simulation(CreadorConexiones factory, SimulationSettings settings) {
        this.factory = factory;
        this.settings = settings;
    }

    // 👇 Añadido el parámetro 'timeoutMs' al final
    public MetricsSnapshot run(Mode mode, Metrics metrics, long timeoutMs) {
        ConnectionPool pool = null;
        ConnectionProvider provider;

        if (mode == Mode.WITH_POOL) {
            try {
                pool = new ConnectionPool(settings.getPoolInicial(), settings.getPoolMaximo(), factory);
            } catch (SQLException e) {
                System.err.println("¡ERROR AL CREAR EL POOL! " + e.getMessage());
                e.printStackTrace();
                return metrics.snapshot(0);
            }
            provider = new PoolConnectionProvider(pool, TIMEOUT_MS);
        } else {
            provider = new DirectConnectionProvider(factory);
        }

        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, settings.getThreads()));
        boolean termino = false;
        
        try {
            long inicio = System.nanoTime();
            for (int i = 0; i < settings.getRequests(); i++) {
                ex.execute(new Cliente(provider, metrics, settings.getReintentos()));
            }
            ex.shutdown();

            try {
                // Si timeoutMs es mayor a 0, usamos ese tiempo. Si es 0, usamos el máximo (10 min).
                if (timeoutMs > 0) {
                    termino = ex.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
                } else {
                    termino = ex.awaitTermination(MAX_MINUTOS, TimeUnit.MINUTES);
                }
            } catch (InterruptedException e) {
                ex.shutdownNow();
                Thread.currentThread().interrupt();
                termino = false;
            }

            if (!termino) {
                // Abortar: interrumpir hilos bloqueados y forzar cierre
                ex.shutdownNow();
                try {
                    ex.awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("⏱️ Timeout en modo " + mode + " (" + timeoutMs + " ms). Abortado.");
            }

            long ms = (System.nanoTime() - inicio) / 1_000_000;
            
            // 👇 Se pasa '!termino' para marcar si fue abortado
            return metrics.snapshot(ms, !termino);

        } finally {
            if (pool != null) pool.close();
        }
    }
}