#!/bin/sh

# create topics
../../kafka_2.11-1.1.0/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic allowed-sensor-ids
../../kafka_2.11-1.1.0/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic allowed-sensor-ids-keyed

# add list of allowed sensors
../../kafka_2.11-1.1.0/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic allowed-sensor-ids < allowed_sensors
