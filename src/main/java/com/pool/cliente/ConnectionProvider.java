package com.pool.cliente;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider { 
    Connection acquire() throws SQLException, InterruptedException;
    void release(Connection conn);
}