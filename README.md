# kafkastreams-workshop

## prerequisites
* Make sure you have JDK8 or higher installed
* Make sure you have Apache Maven installed (optional)
* To get started with kafka, execute steps 1 to 5 from the Kafka quickstart guide. This will get you a running Kafka server.
https://kafka.apache.org/quickstart


## Introduction
We are building a backend application for IoT devices: a set of temperature sensors. These sensors send a lot of data. We want to process the data using a Kafka application.

A starting point can be found under folder "kafkastreams". It includes a simple data model and a web frontend to simulate incoming sensor data.
A complete solution can be found under folder "complete".

A general introduction to Kafka and a massive amount of other documentation can be found here:
https://kafka.apache.org/documentation/


## Exercise 1: Spring Kafka
In this exercise we will use Spring Kafka to setup some simple producing and consuming of messages. We use a JSONSerializer and JSONDeserializer that Spring Kafka provides to write and read our Java object to/from Kafka.

For reference information about Spring Kafka, see https://docs.spring.io/spring-kafka/reference/htmlsingle/


### Setting up Spring Kafka
Add the Spring Kafka dependency to your pom.xml
```
<spring-kafka.version>2.1.6.RELEASE</spring-kafka.version>

<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
  <version>${spring-kafka.version}</version>
</dependency>
```

Add the following annotation to the KafkaWorkshopConfig class:
```
  @EnableKafka
```

This enables the Spring Kafka listeners

The Kafka client library needs to know where to find the Kafka server
Add the following property to your application.properties:
```
  kafka.bootstrap.servers=localhost:9092
```


### Publish sensor data to the topic "received-sensor-data" using a Spring KafkaTemplate
To use a KafkaTemplate we need to setup some configuration and construct the KafkaTemplate. Add the following to the KafkaWorkshopConfig class:
```
@Value("${kafka.bootstrap.servers}")
private String bootstrapServers;

public Map<String, Object> producerConfigs() {
     Map<String, Object> props = new HashMap<>();
     props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
     props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
     props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

     return props;
 }

 public ProducerFactory<String, SensorData> producerFactory() {
     return new DefaultKafkaProducerFactory<>(producerConfigs());
 }

 @Bean
 public KafkaTemplate<String, SensorData> kafkaTemplate() {
     return new KafkaTemplate<>(producerFactory());
 }
 ```

Update the SensorController class by wiring the KafkaTemplate:
```
    private KafkaTemplate<String, SensorData> kafkaTemplate;
```

Now we can implement the publishing of sensor data in the SensorController class using the kafkaTemplate send method.
For specifics, see the Spring Kafka documentation.


### Receive sensor data using a Spring KafkaListener
Make sure you give your Consumer a unique consumer group id. Add the following property to your application.properties
```
application.consumer.groupid=springkafka
```

Before we can use a KafkaListener we need to setup a KafkaListenerContainerFactory. Add the following to your Config class:
```
@Value("${application.consumer.groupid}")
private String groupId;

@Bean
public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<Integer, String>> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<Integer, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setConcurrency(1);
    factory.getContainerProperties().setPollTimeout(1000);
    return factory;
}

public ConsumerFactory<Integer, String> consumerFactory() {
    return new DefaultKafkaConsumerFactory(consumerConfigs(), new StringDeserializer(), new JsonDeserializer<>(SensorData.class));
}

public Map<String, Object> consumerConfigs() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    return props;
}
```

Now we can implement a Kafka Listener class. The Kafka Listener should have a method annotated with:
```
@KafkaListener(topics = TopicNames.RECEIVED_SENSOR_DATA)
```
For specifics, see the Spring Kafka documentation


### Try it out
Congratulations, you have implemented your first Spring Kafka application. You can try it out now.
Make sure Kafka and Zookeeper are running and you have created the required topic.
```
bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic received-sensor-data
```
Browse to http://localhost:8080/sensor and submit some sensor data!

You should also consider adding some unit test coverage. See https://docs.spring.io/spring-kafka/reference/htmlsingle/#testing for details about unit testing Spring Kafka applications


## Exercise 2: Kafka Streams
In this exercise we will be extending the application built in Exercise 1. We will use the plain Kafka Streams library to implement Kafka Streams processing on top of a Spring Boot application

Additional Kafka Streams documentation can be found here:
https://kafka.apache.org/11/documentation/streams/developer-guide/

### Setting up Kafka Streams configuration
Add the Kafka Streams dependency to your pom.xml:
```
<kafka.version>1.1.0</kafka.version>

<dependency>
  <groupId>org.apache.kafka</groupId>
  <artifactId>kafka-streams</artifactId>
  <version>${kafka.version}</version>
</dependency>
```

Set up StreamsConfig and a StreamsBuilder in the Config class:
```
@Value("${application.stream.applicationId}")
private String applicationId;

@Bean
public StreamsConfig streamsConfig() {
    Map<String, Object> props = new HashMap<>();
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    return new StreamsConfig(props);
}

@Bean
public StreamsBuilder streamBuilder() {
    return new StreamsBuilder();
}

```
Make sure you give your Streams application a unique consumer group id. This can not be the same id as the Kafka Consumer from exercise 1! Add the following property to your application.properties
```
application.stream.groupid=kafkastreams
```


### Building a Stream topology and running it in Spring Boot
Create a class KafkaStreamsRunner that implements CommandLineRunner, and wire the StreamsConfig and StreamsBuilder
```
@Component
public class KafkaStreamsRunner implements CommandLineRunner {
```

Override the run method and implement a Streams application that logs all incoming records on the "received-sensor-data" topic.

Building blocks:
* use StreamsBuilder.stream to construct a stream
* use KStream.foreach to log each record  

Tips:
Because Kafka does not know the message format, we will need use a custom JsonSerde to deserialize the SensorData object. We tell the builder to consume messages with the sensorDataSerde:
```
JsonSerde<SensorData> sensorDataSerde = new JsonSerde<>(SensorData.class);
KStream<String, SensorData> sensorDataStream = builder.stream(TopicNames.RECEIVED_SENSOR_DATA, Consumed.with(Serdes.String(), sensorDataSerde));
```

For proper shutdown we need to add a shutdownHook. Use the following code to start the KafkaStreams .
```
KafkaStreams streams = new KafkaStreams(builder.build(), config);
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    streams.close();
}));
streams.start();
```

If you have setup successfully, you now see two log lines in the console logging. One for the Listener and one for the Stream.
As you can see in the console logging, the Stream is executed on a separate Thread.


## Next steps, more advanced Streams function
Now we have a minimal Streams application we can explore the more advanced Streams functions like filtering, mapping, joining, grouping.

### Filter all messages with an ID that starts with "#"
Building blocks:
* use KStream.filter

### Write a record to "low-voltage-alert" whenever SensorData comes in with a voltage lower than 3.
Building blocks:
* use KStream.filter, KStream.map, KStream.to

### Join messages with a kTable of valid sensor ids. And filter all invalid messages.
Building blocks:
* use KStream.join

In the scripts folder of this repository there is a script named "add_allowed_sensors.sh". You can use this script to create the "allowed-sensor-ids" topic and to insert some test data.

The raw data that is inserted does not yet have a key. A solution is to create a KStream for that topic and use KStream.map or KStream.selectKey to convert to a message with a key. Write this stream to a new topic using the KSTream.to method. After that you can create a KTable.

Example:
```
KStream<String, String> idStream = builder.stream(TopicNames.ALLOWED_SENSOR_IDS, Consumed.with(Serdes.String(), Serdes.String()));
idStream.selectKey((k,v) -> v)
        .to(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Produced.with(Serdes.String(),Serdes.String()));
KTable<String, String> idTable = builder.table(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Consumed.with(Serdes.String(), Serdes.String()))
```
We can join with the kTable by using the KStream.join method.

### Group the messages by id, in a hopping time window with a size 5 minutes and an advance interval of 1 minute and calculate average temperature.
Building blocks:
* use KStream.groupBy, windowedBy, aggregate
See https://kafka.apache.org/documentation/streams/developer-guide/dsl-api.html#hopping-time-windows

Log the resulting windows to see how the average is calculated over multiple partially overlapping windows per id.

## Exercise 4: Health Check a Kafka Streams application
TODO, my Spring Actuator health check implementation for Kafka Streams has stopped working in Spring Boot 2 :-)

## Exercise 5: Automated testing
Maybe later :-)
