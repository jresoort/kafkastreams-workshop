package com.jdriven.kafkaworkshop;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

        sensorDataStream.foreach((k,v) -> LOGGER.info("received {}", v.toString()));

        KStream<String, String> idStream = builder.stream(TopicNames.ALLOWED_SENSOR_IDS, Consumed.with(Serdes.String(), Serdes.String()));
        idStream.selectKey((k,v) -> v)
                .to(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Produced.with(Serdes.String(),Serdes.String()));
        KTable<String, String> idTable = builder.table(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Consumed.with(Serdes.String(), Serdes.String()));

        sensorDataStream.join(idTable, (v1, v2) -> v1)
                .foreach((k,v) -> LOGGER.info("This is a valid sensor. {}", v.toString()));

        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
        }));
        streams.start();

    }

}

