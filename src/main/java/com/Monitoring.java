package com;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Monitoring class responsible for tracking sensor status and sending email
 * notifications.
 */
public class Monitoring {
    /**
     * Interval in milliseconds between signal checks.
     */
    public int signalInterval = 5000;

    /**
     * Map of sensor IDs to email addresses.
     */
    private final Map<Integer, List<String>> idToEmailMap = new HashMap<>();

    /**
     * Map of sensor IDs to last signal times.
     */
    private final Map<Integer, Long> idToLastSignalTimeMap = new HashMap<>();

    /**
     * Map of sensor IDs to current status (online/offline).
     */
    private final Map<Integer, String> idToStatusMap = new HashMap<>();

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
        String query = "SELECT id, email, sensor_id FROM emails";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int sensorId = rs.getInt("sensor_id");
                String email = rs.getString("email");

                idToEmailMap.computeIfAbsent(sensorId, k -> new ArrayList<>()).add(email);
                idToStatusMap.putIfAbsent(sensorId, "online"); // Initialize all sensors as offline
                idToLastSignalTimeMap.putIfAbsent(sensorId, System.currentTimeMillis());
            }
        } catch (SQLException e) {
            logger.error("Error loading sensor data from database: ", e);
        }
    }

    /**
     * Prints the sensor ID to email map for debugging purposes.
     */
    public void printIdToEmailMap() {
        idToEmailMap.forEach((sensorId, emails) -> logger
                .info("Sensor ID: " + sensorId + " Status: " + idToStatusMap.get(sensorId) + ", Emails: " + emails));
    }

    /**
     * Signals that a sensor has sent a signal.
     *
     * @param sensorId the ID of the sensor that sent the signal
     */
    public void signalReceived(int sensorId) {
        try {
            signalQueue.put(sensorId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Error adding signal to queue: ", e);
        }
    }

    /**
     * Starts the signal processing thread.
     */
    private void startSignalProcessor() {
        Thread signalProcessorThread = new Thread(() -> {
            while (true) {
                try {
                    int sensorId = signalQueue.take();
                    idToLastSignalTimeMap.put(sensorId, System.currentTimeMillis());
                    updateSensorStatus(sensorId, "online");
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
        long currentTime = System.currentTimeMillis();
        idToLastSignalTimeMap.forEach((sensorId, lastSignalTime) -> {
            if (currentTime - lastSignalTime > signalInterval) {
                updateSensorStatus(sensorId, "offline");
            } else {
                updateSensorStatus(sensorId, "online");
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
        String currentStatus = idToStatusMap.get(sensorId);
        if (!newStatus.equals(currentStatus)) {
            idToStatusMap.put(sensorId, newStatus);
            try {
                emailQueue.put(sensorId); // Add to queue when status changes
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
            while (true) {
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
        List<String> emails = idToEmailMap.get(sensorId);
        if (emails != null) {
            String status = idToStatusMap.get(sensorId);
            for (String email : emails) {
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
        monitoring.printIdToEmailMap();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                logger.info("Checking sensors");
                monitoring.checkSensors();
            }
        }, 0, monitoring.signalInterval);
    }
}