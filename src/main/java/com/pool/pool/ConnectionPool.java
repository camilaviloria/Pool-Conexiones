package com.pool.pool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool implements AutoCloseable { // Implementación de un pool de conexiones que gestiona la creación, adquisición y liberación de conexiones a la base de datos.

    private final BlockingQueue<Connection> libres;
    private final CreadorConexiones factory;
    private final int maximo;
    private final AtomicInteger creadas = new AtomicInteger(0);
    private final AtomicInteger picoEnUso = new AtomicInteger(0);
    private volatile boolean cerrado = false;

    public ConnectionPool(int inicial, int maximo, CreadorConexiones factory) throws SQLException {
        this.factory = factory;
        this.maximo = Math.max(1, maximo);
        this.libres = new LinkedBlockingQueue<>(this.maximo);

        int n = Math.max(0, Math.min(inicial, this.maximo));
        try {
            for (int i = 0; i < n; i++) {
                libres.offer(factory.crear());
                creadas.incrementAndGet();
            }
        } catch (SQLException | RuntimeException e) {
            close();
            throw e;
        }
    }

    public Connection obtener(long timeoutMs) throws SQLException, InterruptedException {
        if (cerrado) throw new SQLException("Pool cerrado");

        Connection c = libres.poll();
        if (c != null) {
            if (esUsable(c)) { updatePico(); return c; }
            descartar(c);
        }

        while (true) {
            int actuales = creadas.get();
            if (actuales >= maximo) break;
            if (creadas.compareAndSet(actuales, actuales + 1)) {
                try {
                    Connection nueva = factory.crear();
                    updatePico();
                    return nueva;
                } catch (SQLException e) {
                    creadas.decrementAndGet();
                    throw e;
                }
            }
        }

        c = libres.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (c == null) throw new SQLException("Timeout esperando conexión");
        return c;
    }

    public void liberar(Connection c) {
        if (c == null) return;
        if (cerrado) { cerrarQuieto(c); return; }
        if (esUsable(c)) libres.offer(c);
        else { descartar(c); }
    }

    @Override
    public void close() {
        cerrado = true;
        Connection c;
        while ((c = libres.poll()) != null) cerrarQuieto(c);
        creadas.set(0);
    }

    public int getLibres()    { return libres.size(); }
    public int getCapacidad() { return maximo; }
    public int getCreadas()   { return creadas.get(); }
    public int getEnUso()     { return Math.max(0, creadas.get() - libres.size()); }
    public int getPicoEnUso() { return picoEnUso.get(); }

    private void updatePico() {
        int enUso = getEnUso();
        picoEnUso.accumulateAndGet(enUso, Math::max);
    }

    private boolean esUsable(Connection c) {
        try { return !c.isClosed(); } catch (SQLException e) { return false; }
    }

    private void descartar(Connection c) {
        cerrarQuieto(c);
        creadas.decrementAndGet();
    }

    private void cerrarQuieto(Connection c) {
        try { c.close(); } catch (SQLException ignored) { }
    }
}