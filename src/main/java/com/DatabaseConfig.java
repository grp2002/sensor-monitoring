package com;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(Config.getDatabaseUrl(), Config.getDatabaseUser(), Config.getDatabasePassword());
    }
}
