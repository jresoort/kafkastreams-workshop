# kafkastreams-workshop

## Prerequisites
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

Now we can implement the publishing of sensor data in the SensorController class. Use the KafkaTemplate.send method to publish sensor data messages.

Some side notes: 
* Publishing messages is done asynchronously. The KafkaTemplate.send method returns a ListenableFuture.
* The KafkaTemplate.send method is overloaded. Use the correct one to publish messages on the "received-sensor-data" topic with the SensorData id as a key, and the SensorData as value.
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

There are a lot more ProducerConfig and ConsumerConfig properties you can set, but for now these are ok. Check the kafka documentation if you want to know more.

Now we can implement a Kafka Listener class. Create a new class, annotate it with @Component and add a method annotated with:
```
@KafkaListener(topics = TopicNames.RECEIVED_SENSOR_DATA)
```
For specifics, see the Spring Kafka documentation


### Try it out
Congratulations, you have implemented your first Spring Kafka application. You can try it out now.
Make sure Kafka and Zookeeper are running and you have created the required topic. (Windows users can use the .bat script)
```
bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic received-sensor-data
```
Browse to http://localhost:8080/sensor and submit some sensor data!

You should also consider adding some unit test coverage. For now we will skip that and continue with Exercise 2 to learn about Kafka Streams. Exercise 3 will cover unit testing Spring Kafka applications.

## Exercise 2: Kafka Streams
In this exercise we will be extending the application built in Exercise 1. We will use the plain Kafka Streams library to implement Kafka Streams processing on top of a Spring Boot application.

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

Create a separate Configuration class for the Kafka Streams stuff. And annotate it with
```
@Configuration
@EnableKafkaStreams
```

Set up a StreamsConfig in the Config class:
```

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

```
Make sure you give your Streams application a unique applicationId. This can not be the same id as the Kafka Consumer groupId from exercise 1! Add the following property to your application.properties
```
application.stream.applicationId=kafkastreams
```


### Building a Stream topology and running it in Spring Boot
Add a bean method to your KafkaStreamConfig class that accepts a StreamsBuilder and returns a KStream.
```
@Bean
public KStream<String, SensorData> kStream(StreamsBuilder builder) {
```

Implement a Streams application that logs all incoming records on the "received-sensor-data" topic.

Building blocks:
* use StreamsBuilder.stream to construct a stream
* use KStream.foreach to log each record  

Tips:
Because Kafka does not know the message format, we will need use a custom JsonSerde to deserialize the SensorData object. We tell the builder to consume messages with the sensorDataSerde:
```
JsonSerde<SensorData> sensorDataSerde = new JsonSerde<>(SensorData.class);
KStream<String, SensorData> sensorDataStream = builder.stream(TopicNames.RECEIVED_SENSOR_DATA, Consumed.with(Serdes.String(), sensorDataSerde));
```


If you have setup successfully, you now see two log lines in the console logging. One for the Listener and one for the Stream.
As you can see in the console logging, the Stream is executed on a separate Thread.


## Next steps, more advanced Streams function
Now we have a minimal Streams application we can explore the more advanced Streams functions like filtering, mapping, joining, grouping.

### Filter all messages with an ID that starts with "#"
Building blocks:
* use KStream.filter

### Write a record to "low-voltage-alert" topic whenever SensorData comes in with a voltage lower than 3.
Possible building blocks:
* use KStream.filter, KStream.map, KStream.to

Tip: You could use a new data format for a low voltage alert, or just go for a simple String value. In any way you will need to pass the KStream.to method a Produced.with(keySerde, valueSerde) to tell Kafka Streams how to serialize the object.

### Join messages with a kTable of valid sensor ids. And filter all invalid messages.
Building blocks:
* use KStream.join 

In the scripts folder of this repository there is a script named "add_allowed_sensors.sh". You can use this script to create the "allowed-sensor-ids" topic and to insert some test data. You might need to adapt the script a bit to match your directory structure by calling it 
like 
```
KAFKA_DIR=/dir/to/kafka add_allowed_sensors.sh
```

The raw data that is inserted does not yet have a key. A solution is to create a KStream for that topic and use KStream.map or KStream.selectKey to convert to a message with a key. Write this stream to a new topic using the KStream.to method. After that you can create a KTable.

Example:
```
KStream<String, String> idStream = builder.stream(TopicNames.ALLOWED_SENSOR_IDS, Consumed.with(Serdes.String(), Serdes.String()));
idStream.selectKey((k,v) -> v)
        .to(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Produced.with(Serdes.String(),Serdes.String()));
KTable<String, String> idTable = builder.table(TopicNames.ALLOWED_SENSOR_IDS_KEYED, Consumed.with(Serdes.String(), Serdes.String()))
```
We can join with the kTable by using the KStream.join method. KStream.join implicity filters messages whenever a join cannot be made. If you don't want that you can use KStream.leftJoin instead.

### Join messages with a kTable of valid sensor ids. And split the stream in two separate streams of valid and invalid messages.
Building blocks:
* use KStream.leftJoin, Kstream.branch

Kstream.branch produces an array of streams.

### Group the messages by id, in a hopping time window with a size 5 minutes and an advance interval of 1 minute and calculate average temperature.
Building blocks:
* use KStream.groupBy, windowedBy, aggregate
See https://kafka.apache.org/documentation/streams/developer-guide/dsl-api.html#hopping-time-windows


## Exercise 3: Unit testing Spring Kafka applications

Spring Kafka comes with some tooling for running an embedded Kafka server in your unit tests.

### Configuring Spring Kafka tests

See https://docs.spring.io/spring-kafka/docs/2.1.6.RELEASE/reference/html/_reference.html#testing for details.

Add the following dependencies to your pom.xml:
```
<dependency>
	<groupId>org.springframework.kafka</groupId>
	<artifactId>spring-kafka-test</artifactId>
	<version>${spring-kafka.version}</version>
</dependency>
<dependency>
	<groupId>log4j</groupId>
	<artifactId>log4j</artifactId>
	<version>1.2.17</version>
	<scope>test</scope>
</dependency>
```	
		
Because we are using the Kafka 1.1 clients, we will need to make sure the right kafka libraries are available during tests:
```
<dependency>
	<groupId>org.apache.kafka</groupId>
	<artifactId>kafka_2.11</artifactId>
	<version>${kafka.version}</version>
	<scope>test</scope>
</dependency>

<dependency>
	<groupId>org.apache.kafka</groupId>
	<artifactId>kafka_2.11</artifactId>
	<version>${kafka.version}</version>
	<classifier>test</classifier>
	<scope>test</scope>
</dependency>
```

To override the configuration during tests, create an application.properties file in src/test/resources.
Add the following properties:
```
kafka.bootstrap.servers=${spring.embedded.kafka.brokers}
application.consumer.groupid=springkafka
application.stream.applicationId=kafkastreams
```

### Enable embedded kafka server
We will extend the KafkaWorkshopApplicationTests to have an embedded Kafka server running.
First we add the DirtiesContext and EmbeddedKafka annotations to our class. We include all topics that are used in our application. The class will look like this:

```
...
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
...
```
Add the KafkaEmbedded that Spring makes available in its context. Also add the SensorController.
```
	@Autowired
	private KafkaEmbedded embeddedKafka;

	@Autowired
	private SensorController controller;
```

### Write the tests for low voltage alert
Create a new test method and use the controller.sensorSubmit method to publish new sensor data and then listen on the embeddedKafka to see if low voltage alerts come in.

Use the following code to consume the alerts. Depending on your message format you might need to use different serializers.
```
Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testGroup", "true", this.embeddedKafka);
consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
Consumer<String, String> consumer = cf.createConsumer();
this.embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TopicNames.LOW_VOLTAGE_ALERT);
ConsumerRecords<String, String> alerts = KafkaTestUtils.getRecords(consumer);
```

You should now be able to receive the alerts. Write assertions to check if the correct alerts are received.

If you like, you can add test cases for the other message flows.





Log the resulting windows to see how the average is calculated over multiple partially overlapping windows per id.
