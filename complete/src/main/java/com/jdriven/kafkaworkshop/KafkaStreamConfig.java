package com.jdriven.kafkaworkshop;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.kstream.internals.WindowedDeserializer;
import org.apache.kafka.streams.kstream.internals.WindowedSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class KafkaStreamConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaStreamConfig.class);

    @Value("${kafka.bootstrap.servers}")
    private String bootstrapServers;

    @Value("${application.stream.applicationId}")
    private String applicationId;


    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public StreamsConfig streamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        return new StreamsConfig(props);
    }

    @Bean
    public KStream<String, SensorData> kStream(StreamsBuilder builder) {

        JsonSerde<SensorData> sensorDataSerde = new JsonSerde<>(SensorData.class);
        KStream<String, SensorData> sensorDataStream = builder.stream(TopicNames.RECEIVED_SENSOR_DATA, Consumed.with(Serdes.String(), sensorDataSerde));

        //Filter all messages with an ID that starts with "#"
        sensorDataStream
                .filter( (k, v) -> v.getId().startsWith("#"))
                .foreach((k,v) -> LOGGER.info("received {}", v.toString()));

        //Write a record to "low-voltage-alert" topic whenever SensorData comes in with a voltage lower than 3.
        sensorDataStream
                .filter( (k, v) -> v.getVoltage() < 3d)
                .mapValues( v -> "Low voltage detected for sensor "+ v.getId())
                .peek( (k, v) -> LOGGER.info("writing message to LOW_VOLTAGE_ALERT topic: " + v))
                .to(TopicNames.LOW_VOLTAGE_ALERT, Produced.with(Serdes.String(), Serdes.String()));


        //Setup kTable with valid sensor IDs
        KStream<String, String> idStream = builder.stream(TopicNames.ALLOWED_SENSOR_IDS, Consumed.with(Serdes.String(), Serdes.String()));
        idStream.selectKey((k,v) -> v)
                .to(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Produced.with(Serdes.String(),Serdes.String()));
        KTable<String, String> idTable = builder.table(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Consumed.with(Serdes.String(), Serdes.String()));

        //Join messages with a kTable of valid sensor IDs. And filter all invalid messages.
        sensorDataStream.join(idTable, (v1, v2) -> v1)
                .foreach((k,v) -> LOGGER.info("This is a valid sensor. {}", v.toString()));


        //Group the messages by id, in a hopping time window with a size 5 minutes and an advance interval of 1 minute and calculate average temperature.
        sensorDataStream.groupByKey()
                .windowedBy(TimeWindows.of(300000).advanceBy(100000))
                .aggregate(SumCount::new, (key, value, aggregate) -> aggregate.addValue(value.getTemperature()), Materialized.with(Serdes.String(), new JsonSerde<>(SumCount.class)))
                .mapValues(SumCount::average, Materialized.with(new WindowedSerde<>(Serdes.String()), Serdes.Double()))
                .toStream()
                .map(((key, average) -> new KeyValue<>(key.key(), new Average(average, key.window().start(), key.window().start() + 30000))))
                //write to topic and continue the stream processing using KStream.through
                .through(TopicNames.AVERAGE_TEMPS, Produced.with(Serdes.String(), new JsonSerde<>(Average.class)))
                .foreach((key, average) -> LOGGER.info(String.format("Received average %s for id %s on %s", average, key, TopicNames.AVERAGE_TEMPS)));

        return sensorDataStream;
    }



static class WindowedSerde<T> implements Serde<Windowed<T>> {

    private final Serde<Windowed<T>> inner;

    public WindowedSerde(Serde<T> serde) {
        inner = Serdes.serdeFrom(
                new WindowedSerializer<>(serde.serializer()),
                new WindowedDeserializer<>(serde.deserializer()));
    }

    @Override
    public Serializer<Windowed<T>> serializer() {
        return inner.serializer();
    }

    @Override
    public Deserializer<Windowed<T>> deserializer() {
        return inner.deserializer();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        inner.serializer().configure(configs, isKey);
        inner.deserializer().configure(configs, isKey);
    }

    @Override
    public void close() {
        inner.serializer().close();
        inner.deserializer().close();
    }

}

static class Average {
    double average;
    long start;
    long end;

    public Average(final double average, final long start, final long end) {
        this.average = average;
        this.start = start;
        this.end = end;
    }

    public Average() {
    }

    public double getAverage() {
        return average;
    }

    public void setAverage(final double average) {
        this.average = average;
    }

    public long getStart() {
        return start;
    }

    public void setStart(final long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(final long end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return String.format("%.2f for %2$tH:%2$tM:%2$tS-%3$tH:%3$tM:%3$tS", average, new Date(start), new Date(end));
    }
}

static class SumCount {
    double sum;
    int count;

    public SumCount() {
    }

    public SumCount addValue(double value) {
        sum += value;
        count++;
        return this;
    }

    public double average() {
        return sum / count;
    }

    public double getSum() {
        return sum;
    }

    public void setSum(final double sum) {
        this.sum = sum;
    }

    public int getCount() {
        return count;
    }

    public void setCount(final int count) {
        this.count = count;
    }
}

}
