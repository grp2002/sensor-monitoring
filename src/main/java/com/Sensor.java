package com;

import java.util.List;

public class Sensor {
    private final int id;
    private String status;
    private String lastTimestamp;
    private final List<String> emails;

    private int interval;

    public Sensor(int id, String status, List<String> emails, int interval) {
        this.id = id;
        this.status = status;
        this.emails = emails;
        this.interval = interval;
    }

    public int getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(String lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    public void setInterval(int interval){
        this.interval = interval;
    }

    public int getInterval(){
        return this.interval;
    }

    public List<String> getEmails() {
        return emails;
    }
}
