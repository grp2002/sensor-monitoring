package com;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getCheckInterval() {
        return Integer.parseInt(properties.getProperty("checkInterval", "5000"));
    }

    public static String getDatabaseUrl() {
        return properties.getProperty("db.url") + properties.getProperty("db.name");
    }

    public static String getDatabaseUser() {
        return properties.getProperty("db.user");
    }

    public static String getDatabasePassword() {
        return properties.getProperty("db.password");
    }

    public static String getTimestampTable() {
        return properties.getProperty("db.timestamp.table");
    }

    public static String getTimestampSensorIdColumn() {
        return properties.getProperty("db.timestamp.sensorIdColumn");
    }

    public static String getTimestampColumn() {
        return properties.getProperty("db.timestamp.timestampColumn");
    }

    public static String getEmailTable() {
        return properties.getProperty("db.email.table");
    }

    public static String getEmailSensorIdColumn() {
        return properties.getProperty("db.email.sensorIdColumn");
    }

    public static String getEmailColumn() {
        return properties.getProperty("db.email.emailColumn");
    }

    public static String getIntervalTable() {
        return properties.getProperty("db.interval.table");
    }

    public static String getIntervalSensorIdColumn() {
        return properties.getProperty("db.interval.sensorIdColumn");
    }

    public static String getIntervalColumn() {
        return properties.getProperty("db.interval.intervalColumn");
    }
}
