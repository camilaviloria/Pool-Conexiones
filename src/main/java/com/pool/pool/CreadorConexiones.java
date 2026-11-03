package com.pool.pool;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface CreadorConexiones {
    Connection crear() throws SQLException;
}