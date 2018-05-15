package com.jdriven.kafkaworkshop;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Consumed;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

@Component
public class KafkaStreamsRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaStreamsRunner.class);

    private StreamsConfig config;
    private StreamsBuilder builder;

    public KafkaStreamsRunner(StreamsConfig config, StreamsBuilder builder) {
        this.config = config;
        this.builder = builder;
    }

    @Override
    public void run(String... arg0) throws Exception {

        JsonSerde<SensorData> sensorDataSerde = new JsonSerde<>(SensorData.class);
        KStream<String, SensorData> sensorDataStream = builder.stream(TopicNames.RECEIVED_SENSOR_DATA, Consumed.with(Serdes.String(), sensorDataSerde));

        sensorDataStream.foreach((key, value) -> LOGGER.info("received {}", value.toString()));

        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
        }));
        streams.start();

    }
}

