package com.pool.cliente;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider { // Interfaz que define los métodos para adquirir y liberar conexiones a la base de datos.
    Connection acquire() throws SQLException, InterruptedException;
    void release(Connection conn);
}