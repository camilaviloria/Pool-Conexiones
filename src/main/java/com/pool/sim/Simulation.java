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

    private final CreadorConexiones factory;
    private final SimulationSettings settings;

    public Simulation(CreadorConexiones factory, SimulationSettings settings) {
        this.factory = factory;
        this.settings = settings;
    }

    public MetricsSnapshot run(Mode mode, Metrics metrics) {
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

        try {
            long inicio = System.nanoTime();
            ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, settings.getThreads()));
            for (int i = 0; i < settings.getRequests(); i++) {
                ex.execute(new Cliente(provider, metrics, settings.getReintentos()));
            }
            ex.shutdown();
            try {
                if (!ex.awaitTermination(MAX_MINUTOS, TimeUnit.MINUTES)) ex.shutdownNow();
            } catch (InterruptedException e) {
                ex.shutdownNow();
                Thread.currentThread().interrupt();
            }
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            return metrics.snapshot(ms);
        } finally {
            if (pool != null) pool.close();
        }
    }
}