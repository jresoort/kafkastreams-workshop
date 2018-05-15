package com.jdriven.kafkaworkshop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SensorDataKafkaListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorDataKafkaListener.class);

    @KafkaListener(topics = TopicNames.RECEIVED_SENSOR_DATA)
    public void listen(SensorData sensorData) {
        LOGGER.info("received {}", sensorData.toString());
    }
}
