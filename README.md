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


## Exercise 1: Spring Kafka
In this exercise we will use Spring Kafka to setup some simple producing and consuming of messages. We use a JSONSerializer and JSONDeserializer that Spring Kafka provides to write and read our Java object to/from Kafka.

For reference information about Spring Kafka, see https://docs.spring.io/spring-kafka/reference/htmlsingle/


### Setting up Spring Kafka
Add the Spring Kafka dependency to your pom.xml
```
<spring-kafka.version>1.3.3.RELEASE</spring-kafka.version>

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
Make sure Kafka and Zookeeper are running and you have created the required topic.
bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic received-sensor-data
Browse to http://localhost:8080/sensor and submit some sensor data!







Nu zijn er wat sensors kapot. Zorg er voor dat deze eruit gefilterd worden
