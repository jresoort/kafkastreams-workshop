package com.jdriven.kafkaworkshop;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.concurrent.ListenableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest
@EmbeddedKafka(topics = TopicNames.RECEIVED_SENSOR_DATA)
public class SensorDataKafkaListenerTest {

    @Autowired
    private KafkaTemplate<String, SensorData> template;

    @Rule
    public OutputCapture stdout = new OutputCapture();

    @Test
    public void sendReceive() throws Exception {
        SensorData sensorData = new SensorData();
        sensorData.setId("my-sensor");
        sensorData.setTemperature(21.3);
        sensorData.setVoltage(4.5);

        ListenableFuture<SendResult<String, SensorData>> send = template.send(TopicNames.RECEIVED_SENSOR_DATA, sensorData);
        System.out.println(send.get().getProducerRecord());
        System.out.println(send.get().getRecordMetadata());

        await().untilAsserted(() -> assertThat(stdout.toString()).contains("received SensorData"));
    }

}
