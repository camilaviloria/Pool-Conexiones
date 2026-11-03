package com.pool.cliente;

import com.pool.metrics.Metrics;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Cliente implements Runnable {

    private static final String[] QUERIES = {
            "SELECT * FROM notas LIMIT 1",
            "SELECT * FROM notas ORDER BY id DESC LIMIT 5",
            "SELECT COUNT(*) FROM notas",
            "SELECT * FROM notas WHERE id = 1"
    };

    private final ConnectionProvider provider;
    private final Metrics metrics;
    private final int maxReintentos;

    public Cliente(ConnectionProvider provider, Metrics metrics, int maxReintentos) {
        this.provider = provider;
        this.metrics = metrics;
        this.maxReintentos = maxReintentos;
    }

    @Override
    public void run() {
        String sql = QUERIES[(int) (Math.random() * QUERIES.length)];
        long inicio = System.nanoTime();

        boolean ok = false;
        int reintentos = 0;
        for (int intento = 0; intento <= maxReintentos && !ok; intento++) {
            if (intento > 0) {
                reintentos++;
                try { Thread.sleep(10L * intento); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                ejecutar(sql);
                ok = true;
           } catch (SQLException | InterruptedException | RuntimeException e) {
    System.err.println("Error en consulta: " + e.getMessage()); // <--- Agrega esta línea
}
        }

        metrics.record(ok, System.nanoTime() - inicio, reintentos);
    }

    private void ejecutar(String sql) throws SQLException, InterruptedException {
        Connection c = provider.acquire();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
        } finally {
            provider.release(c);
        }
    }
}