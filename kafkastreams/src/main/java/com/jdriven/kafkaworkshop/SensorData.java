package com.jdriven.kafkaworkshop;

public class SensorData {

    private String id;
    private double temperature;
    private double voltage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getVoltage() {
        return voltage;
    }

    public void setVoltage(double voltage) {
        this.voltage = voltage;
    }

    @Override
    public String toString() {
        return "SensorData{" +
                "id='" + id + '\'' +
                ", temperature=" + temperature +
                ", voltage=" + voltage +
                '}';
    }
}
