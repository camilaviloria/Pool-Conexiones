package com.pool.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.pool.pool.CreadorConexiones;

public class JdbcConnectionFactory implements CreadorConexiones {

    private final String driverClass;
    private final String url;
    private final String user;
    private final String password;

    public JdbcConnectionFactory(String driverClass, String url, String user, String password) {
        this.driverClass = driverClass;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public Connection crear() throws SQLException {
        try { 
            Class.forName(driverClass); 
        } catch (ClassNotFoundException e) {
            throw new SQLException("Falta el driver JDBC (" + driverClass + ").", e);
        }

        // 🔥 TIMEOUT DE 1 SEGUNDO PARA EVITAR QUE SE CUELGUE EN EL COLAPSO
        String urlConTimeouts = this.url;
        if (urlConTimeouts.contains("?")) {
            urlConTimeouts += "&connectTimeout=1&socketTimeout=1";
        } else {
            urlConTimeouts += "?connectTimeout=1&socketTimeout=1";
        }

        return DriverManager.getConnection(urlConTimeouts, user, password);
    }
}