package com.pool.db;

import com.pool.pool.CreadorConexiones;
import com.pool.util.AppConfig;

public enum DatabaseType { 

    POSTGRES("PostgreSQL", "org.postgresql.Driver", "postgres",
            "jdbc:postgresql://localhost:5432/mynotes_db", "postgres"),

    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "mysql",
            "jdbc:mysql://localhost:3306/mynotes_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            "root"),

    SIMULADO("Simulado (sin BD)", null, null, null, null); 

    private final String label;
    private final String driverClass;
    private final String prefix;
    private final String defaultUrl;
    private final String defaultUser;

    DatabaseType(String label, String driverClass, String prefix, String url, String user) {
        this.label = label;
        this.driverClass = driverClass;
        this.prefix = prefix;
        this.defaultUrl = url;
        this.defaultUser = user;
    }

    public CreadorConexiones createFactory() {
        if (this == SIMULADO) return new FakeConnectionFactory();
        return new JdbcConnectionFactory(
                driverClass,
                AppConfig.getString(prefix + ".url", defaultUrl),
                AppConfig.getString(prefix + ".user", defaultUser),
                AppConfig.getString(prefix + ".password", ""));
    }

    @Override
    public String toString() { return label; }
}