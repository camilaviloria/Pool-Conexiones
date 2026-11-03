package com.pool.cliente;

import java.sql.Connection;
import java.sql.SQLException;

import com.pool.pool.CreadorConexiones;

public class DirectConnectionProvider implements ConnectionProvider {

    private final CreadorConexiones factory;

    public DirectConnectionProvider(CreadorConexiones factory) { this.factory = factory; }

    @Override
    public Connection acquire() throws SQLException { return factory.crear(); }

    @Override
    public void release(Connection conn) {
        try { if (conn != null) conn.close(); }
        catch (SQLException ignored) { }
    }
}