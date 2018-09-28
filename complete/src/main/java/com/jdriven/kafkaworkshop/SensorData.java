package com.jdriven.kafkaworkshop;

import lombok.Data;

@Data
public class SensorData {

    private String id;
    private double temperature;
    private double voltage;

}
