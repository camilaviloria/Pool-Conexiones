package com.pool.cliente;

import java.sql.Connection;
import java.sql.SQLException;

import com.pool.pool.ConnectionPool;

public class PoolConnectionProvider implements ConnectionProvider { // Implementación de ConnectionProvider que utiliza un ConnectionPool para adquirir y liberar conexiones.

    private final ConnectionPool pool; // Referencia al ConnectionPool que se utilizará para gestionar las conexiones.
    private final long timeoutMs; // Tiempo máximo de espera para adquirir una conexión del pool en milisegundos.

    public PoolConnectionProvider(ConnectionPool pool, long timeoutMs) { // Constructor que recibe un ConnectionPool y un tiempo de espera en milisegundos.
        this.pool = pool;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Connection acquire() throws SQLException, InterruptedException { // Método para adquirir una conexión del pool, con un tiempo de espera especificado.
        return pool.obtener(timeoutMs);
    }

    @Override
    public void release(Connection conn) { pool.liberar(conn); } // Método para liberar una conexión de vuelta al pool.
}