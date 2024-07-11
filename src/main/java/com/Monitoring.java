package com;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitoring class responsible for tracking sensor status and sending email
 * notifications.
 */
public class Monitoring {
    /**
     * Map of sensor IDs to Sensor objects.
     */
    private final Map<Integer, Sensor> sensors = new ConcurrentHashMap<>();

    /**
     * Logger instance for logging events.
     */
    private static final Logger logger = LogManager.getLogger(Monitoring.class);

    /**
     * Blocking queue for signal processing.
     */
    private final BlockingQueue<Integer> signalQueue = new ArrayBlockingQueue<>(1024);

    /**
     * Blocking queue for email processing.
     */
    private final BlockingQueue<Integer> emailQueue = new ArrayBlockingQueue<>(1024);

    /**
     * Constructor initializes sensor data and starts signal and email processing
     * threads.
     */
    public Monitoring() {
        loadSensorData();
        startSignalProcessor();
        startEmailProcessor();
    }


    /**
     * Loads sensor data from the database.
     */
    private void loadSensorData() {
        String timestampTable = Config.getTimestampTable();
        String timestampSensorIdColumn = Config.getTimestampSensorIdColumn();
        String timestampColumn = Config.getTimestampColumn();

        String timestampQuery = String.format("SELECT %s, MAX(%s) AS latestTimestamp FROM %s GROUP BY %s",
                timestampSensorIdColumn, timestampColumn, timestampTable, timestampSensorIdColumn);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(timestampQuery);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int sensorId = rs.getInt(timestampSensorIdColumn);
                String latestTimestamp = rs.getString("latestTimestamp");

                sensors.putIfAbsent(sensorId, new Sensor(sensorId, "online", new ArrayList<>(), 0));
                sensors.get(sensorId).setLastTimestamp(latestTimestamp);
            }
        } catch (SQLException e) {
            logger.error("Error loading sensor data from database: ", e);
        }

        String emailTable = Config.getEmailTable();
        String emailSensorIdColumn = Config.getEmailSensorIdColumn();
        String emailColumn = Config.getEmailColumn();

        String emailQuery = String.format("SELECT %s, %s FROM %s",
                emailSensorIdColumn, emailColumn, emailTable);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement emailStmt = conn.prepareStatement(emailQuery);
             ResultSet emailRs = emailStmt.executeQuery()) {

            while (emailRs.next()) {
                int sensorId = emailRs.getInt(emailSensorIdColumn);
                String email = emailRs.getString(emailColumn);

                sensors.computeIfAbsent(sensorId, k -> new Sensor(sensorId, "online", new ArrayList<>(), 0)).getEmails().add(email);
            }
        } catch (SQLException e) {
            logger.error("Error loading email data from database: ", e);
        }
        String intervalTable = Config.getIntervalTable();
        String intervalSensorIdColumn = Config.getIntervalSensorIdColumn();
        String intervalColumn = Config.getIntervalColumn();

        String intervalQuery = String.format("SELECT %s, %s FROM %s",
                intervalSensorIdColumn, intervalColumn, intervalTable);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement intervalStmt = conn.prepareStatement(intervalQuery);
             ResultSet intervalRs = intervalStmt.executeQuery()) {

            while (intervalRs.next()) {
                int sensorId = intervalRs.getInt(intervalSensorIdColumn);
                int interval = intervalRs.getInt(intervalColumn);

                sensors.computeIfAbsent(sensorId, k -> new Sensor(sensorId, "online", new ArrayList<>(), 0)).setInterval(interval);
            }
        } catch (SQLException e) {
            logger.error("Error loading interval data from database: ", e);
        }
    }

    private void updateAllSensorTimestamps() {
        String timestampTable = Config.getTimestampTable();
        String timestampSensorIdColumn = Config.getTimestampSensorIdColumn();
        String timestampColumn = Config.getTimestampColumn();

        String query = String.format("SELECT %s, MAX(%s) AS latestTimestamp FROM %s GROUP BY %s",
                timestampSensorIdColumn, timestampColumn, timestampTable, timestampSensorIdColumn);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int sensorId = rs.getInt(timestampSensorIdColumn);
                String latestTimestamp = rs.getString("latestTimestamp");
                Sensor sensor = sensors.get(sensorId);
                if (sensor != null) {
                    sensor.setLastTimestamp(latestTimestamp);
                }
            }
            logger.info("Updated timestamp");
        } catch (SQLException e) {
            logger.error("Error updating sensor timestamps from database: ", e);
        }
    }



    /**
     * Prints the sensor data for debugging purposes.
     */
    public void printSensors() {
        sensors.forEach((sensorId, sensor) -> logger.info("Sensor ID: " + sensor.getId() +
                " Status: " + sensor.getStatus() + ", Last Timestamp: " + sensor.getLastTimestamp() + ", Emails: " + sensor.getEmails() + ", Time intervals: " + sensor.getInterval() + "min"));
    }

    /**
     * Starts the signal processing thread.
     */
    private void startSignalProcessor() {
        Thread signalProcessorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int sensorId = signalQueue.take();
                    sensors.get(sensorId).setStatus("online");
                    sensors.get(sensorId).setLastTimestamp(new Date().toString());
                    logger.info("Processed signal for sensor ID: " + sensorId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Signal processor interrupted: ", e);
                }
            }
        });
        signalProcessorThread.setDaemon(true);
        signalProcessorThread.start();
    }

    /**
     * Checks the status of all sensors and updates their status if necessary.
     */
    public void checkSensors() {
        updateAllSensorTimestamps();
        long currentTime = System.currentTimeMillis();
        sensors.forEach((sensorId, sensor) -> {
            try {
                long lastSignalTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(sensor.getLastTimestamp()).getTime();
                long intervalMillis = (long) sensor.getInterval() * 60 * 1000; // Convert interval to milliseconds
                if (currentTime - lastSignalTime > intervalMillis) {
                    updateSensorStatus(sensorId, "offline");
                } else {
                    updateSensorStatus(sensorId, "online");
                }
            } catch (Exception e) {
                logger.error("Error parsing timestamp for sensor ID: " + sensorId, e);
            }
        });
    }

    /**
     * Updates the status of a sensor and adds it to the email queue if the status
     * has changed.
     *
     * @param sensorId  the ID of the sensor to update
     * @param newStatus the new status of the sensor (online/offline)
     */
    private void updateSensorStatus(int sensorId, String newStatus) {
        Sensor sensor = sensors.get(sensorId);
        String currentStatus = sensor.getStatus();
        if (!newStatus.equals(currentStatus)) {
            sensor.setStatus(newStatus);
            try {
                emailQueue.put(sensorId); // Add to queue when status changes
                logger.info("Status changed for sensor ID: " + sensorId + " to " + newStatus);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Error adding status change to queue: ", e);
            }
        }
    }

    /**
     * Starts the email processing thread.
     */
    private void startEmailProcessor() {
        Thread emailProcessorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int sensorId = emailQueue.take();
                    sendEmail(sensorId);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Email processor interrupted: ", e);
                }
            }
        });
        emailProcessorThread.setDaemon(true);
        emailProcessorThread.start();
    }

    /**
     * Sends an email notification for a sensor status change.
     *
     * @param sensorId the ID of the sensor that triggered the email
     */
    private void sendEmail(int sensorId) {
        Sensor sensor = sensors.get(sensorId);
        if (sensor != null) {
            String status = sensor.getStatus();
            for (String email : sensor.getEmails()) {
                if ("offline".equals(status)) {
                    logger.info("Sending email to: " + email + " - Sensor ID " + sensorId + " is offline.");
                } else {
                    logger.info("Sending email to: " + email + " - Sensor ID " + sensorId + " is online.");
                }
            }
            logger.info("Email sent for sensor ID: " + sensorId);
        }
    }
    public static void main(String[] args) {
        Monitoring monitoring = new Monitoring();
        monitoring.printSensors();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                logger.info("Checking sensors");
                monitoring.checkSensors();
            }
        }, 0, Config.getCheckInterval());
    }
}
