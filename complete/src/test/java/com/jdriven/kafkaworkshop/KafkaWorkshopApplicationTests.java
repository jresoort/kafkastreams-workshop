package com.jdriven.kafkaworkshop;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1,
		topics = {
				TopicNames.RECEIVED_SENSOR_DATA,
				TopicNames.LOW_VOLTAGE_ALERT,
				TopicNames.ALLOWED_SENSOR_IDS_KEYED,
				TopicNames.ALLOWED_SENSOR_IDS,
				TopicNames.AVERAGE_TEMPS})
public class KafkaWorkshopApplicationTests {

	@Test
	public void contextLoads() {
	}

	@Autowired
	private KafkaEmbedded embeddedKafka;

	@Autowired
	private SensorController controller;

	@Test
	public void lowVoltage() throws Exception {
		SensorData data1 = new SensorData();
		data1.setId("1111");
		data1.setTemperature(20.1d);
		data1.setVoltage(2.6d);
		controller.sensorSubmit(data1);

		SensorData data2 = new SensorData();
		data2.setId("2222");
		data2.setTemperature(20.1d);
		data2.setVoltage(3.5d);
		controller.sensorSubmit(data2);

		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testGroup", "true", this.embeddedKafka);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
		Consumer<String, String> consumer = cf.createConsumer();
		this.embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TopicNames.LOW_VOLTAGE_ALERT);
		ConsumerRecords<String, String> alerts = KafkaTestUtils.getRecords(consumer);

		Assert.assertEquals("amount of alerts not correct", 1, alerts.count());
		Assert.assertTrue("incorrect alert", alerts.iterator().next().value().contains("1111"));
	}


}
