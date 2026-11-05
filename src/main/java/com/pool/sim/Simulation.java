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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Simulation {

    public enum Mode { WITH_POOL, WITHOUT_POOL }

    private static final long ACQUIRE_TIMEOUT_MS = 1_000; 
    private static final long MAX_MINUTOS = 10;

    private final CreadorConexiones factory;
    private final SimulationSettings settings;

    public Simulation(CreadorConexiones factory, SimulationSettings settings) {
        this.factory = factory;
        this.settings = settings;
    }

    public MetricsSnapshot run(Mode mode, Metrics metrics, long timeoutMs) {
        ConnectionPool pool = null;
        ConnectionProvider provider;

        if (mode == Mode.WITH_POOL) {
            try {
                pool = new ConnectionPool(settings.getPoolInicial(), settings.getPoolMaximo(), factory);
            } catch (SQLException e) {
                System.err.println("¡ERROR AL CREAR EL POOL! " + e.getMessage());
                return metrics.snapshot(0);
            }
            provider = new PoolConnectionProvider(pool, ACQUIRE_TIMEOUT_MS);
        } else {
            provider = new DirectConnectionProvider(factory);
        }

        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, settings.getThreads()));
        boolean termino = false;

        CountDownLatch barreraDeSalida = new CountDownLatch(1);

        try {
            long inicio = System.nanoTime();

            for (int i = 0; i < settings.getRequests(); i++) {
                ex.execute(() -> {
                    try {
                        barreraDeSalida.await();
                        new Cliente(provider, metrics, settings.getReintentos()).run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            barreraDeSalida.countDown();
            
            ex.shutdown();

            try {
                if (timeoutMs > 0) {
                    // Aquí espera el tiempo límite (ej. 4000 ms)
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
                // 🔥 ABORTO INMEDIATO: Matamos los hilos y NO esperamos tiempo de gracia
                ex.shutdownNow(); 
                System.err.println("⏱️ Timeout en modo " + mode + " (" + timeoutMs + " ms). Abortado.");
            }

            long ms = (System.nanoTime() - inicio) / 1_000_000;
            return metrics.snapshot(ms, !termino);

        } finally {
            if (pool != null) pool.close();
        }
    }
}