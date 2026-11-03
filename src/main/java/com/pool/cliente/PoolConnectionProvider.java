package com.pool.cliente;

import java.sql.Connection;
import java.sql.SQLException;

import com.pool.pool.ConnectionPool;

public class PoolConnectionProvider implements ConnectionProvider {

    private final ConnectionPool pool;
    private final long timeoutMs;

    public PoolConnectionProvider(ConnectionPool pool, long timeoutMs) {
        this.pool = pool;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Connection acquire() throws SQLException, InterruptedException {
        return pool.obtener(timeoutMs);
    }

    @Override
    public void release(Connection conn) { pool.liberar(conn); }
}