package com.pool.sim;

import com.pool.cliente.Cliente;
import com.pool.cliente.ConnectionProvider;
import com.pool.cliente.DirectConnectionProvider;
import com.pool.cliente.PoolConnectionProvider;
import com.pool.metrics.Metrics;
import com.pool.metrics.MetricsSnapshot;
import com.pool.pool.ConnectionPool;
import com.pool.pool.CreadorConexiones;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Simulation {

    public enum Mode { WITH_POOL, WITHOUT_POOL }

    private static final long ACQUIRE_TIMEOUT_MS = 5_000; 
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
            int intentos = 0;
            boolean exito = false;
            ConnectionPool poolTemporal = null;
            
            while (intentos < 2 && !exito) {
                try {
                    System.out.println("Preparando el Pool... (Intento " + (intentos+1) + ")");
                    Thread.sleep(1000); 
                    
                    poolTemporal = new ConnectionPool(settings.getPoolInicial(), settings.getPoolMaximo(), factory);
                    exito = true;
                } catch (Exception e) {
                    intentos++;
                    System.err.println("Fallo al crear el Pool: " + e.getMessage());
                    if (intentos >= 2) {
                        System.err.println("EL POOL NO PUDO INICIAR. Reinicia PostgreSQL.");
                        // Aquí usamos settings.getRequests() para saber cuántas fallaron
                        return metrics.snapshot(0, true, settings.getRequests());
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
            
            pool = poolTemporal;
            provider = new PoolConnectionProvider(pool, ACQUIRE_TIMEOUT_MS);
        } else {
            provider = new DirectConnectionProvider(factory);
        }

        // Aquí usamos settings.getThreads()
        int totalHilos = Math.max(1, settings.getThreads());
        
        ExecutorService ex = Executors.newFixedThreadPool(totalHilos);
        boolean termino = false;

        // Aquí usamos settings.getRequests() para el contador
        AtomicInteger peticionesRestantes = new AtomicInteger(settings.getRequests());
        CountDownLatch barreraDeSalida = new CountDownLatch(1);

        try {
            long inicio = System.nanoTime();

            for (int i = 0; i < totalHilos; i++) {
                ex.execute(() -> {
                    try {
                        barreraDeSalida.await();
                        while (peticionesRestantes.getAndDecrement() > 0) {
                            // Aquí usamos settings.getReintentos()
                            new Cliente(provider, metrics, settings.getReintentos()).run();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            barreraDeSalida.countDown();
            ex.shutdown();

            try {
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
                ex.shutdownNow();
                System.err.println("⏱️ Timeout en modo " + mode + " (" + timeoutMs + " ms). Abortado.");
            }

            long ms = (System.nanoTime() - inicio) / 1_000_000;
            return metrics.snapshot(ms, !termino, settings.getRequests());

        } finally {
            if (pool != null) pool.close();
        }
    }
}