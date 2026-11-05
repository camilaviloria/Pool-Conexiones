package com.pool.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.pool.pool.CreadorConexiones;

/**
 * BD simulada: abrir conexión ~25 ms, query 2-6 ms, límite 60 conexiones.
 * Sirve para comparar pool vs sin pool sin instalar nada.
 */
public class FakeConnectionFactory implements CreadorConexiones {

    private static final int MAX_OPEN = 1000;
    private static final int CONNECT_MS = 5;

    private final AtomicInteger abiertas = new AtomicInteger();

    @Override
    public Connection crear() throws SQLException {
        if (abiertas.incrementAndGet() > MAX_OPEN) {
            abiertas.decrementAndGet();
            throw new SQLException("FATAL: too many connections (simulado, límite " + MAX_OPEN + ")");
        }
        try { Thread.sleep(CONNECT_MS); }
        catch (InterruptedException e) {
            abiertas.decrementAndGet();
            Thread.currentThread().interrupt();
            throw new SQLException("Interrumpido", e);
        }
        return proxy(Connection.class, new ConnHandler());
    }

    private class ConnHandler extends BaseHandler {
        private final AtomicBoolean closed = new AtomicBoolean();
        @Override
        Object handle(Method m, Object[] args) throws SQLException {
            switch (m.getName()) {
                case "close":
                    if (closed.compareAndSet(false, true)) abiertas.decrementAndGet();
                    return null;
                case "isClosed": return closed.get();
                case "isValid": return !closed.get();
                case "createStatement":
                    if (closed.get()) throw new SQLException("Conexión cerrada");
                    return proxy(Statement.class, new StmtHandler());
                default: return null;
            }
        }
    }

    private static class StmtHandler extends BaseHandler {
        @Override
        Object handle(Method m, Object[] args) throws SQLException {
            if ("executeQuery".equals(m.getName())) {
                try { Thread.sleep(ThreadLocalRandom.current().nextInt(2, 7)); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrumpido", e);
                }
                return proxy(ResultSet.class, new RsHandler());
            }
            return null;
        }
    }

    private static class RsHandler extends BaseHandler {
        private final AtomicBoolean consumed = new AtomicBoolean();
        @Override
        Object handle(Method m, Object[] args) {
            if ("next".equals(m.getName())) return consumed.compareAndSet(false, true);
            return null;
        }
    }

    private abstract static class BaseHandler implements InvocationHandler {
        abstract Object handle(Method m, Object[] args) throws SQLException;
        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            switch (m.getName()) {
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == args[0];
                case "toString": return "FakeJdbc";
                default:
                    Object r = handle(m, args);
                    return r != null ? r : defaultVal(m.getReturnType());
            }
        }
    }

    private static Object defaultVal(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class)     return 0;
        if (t == long.class)    return 0L;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }
}