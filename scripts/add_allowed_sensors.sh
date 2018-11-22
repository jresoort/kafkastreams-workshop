#!/bin/sh

if [ "${KAFKA_DIR}" == "" ]; then
  KAFKA_DIR=../../kafka_2.11-1.1.0
fi

# create topics
${KAFKA_DIR}/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic allowed-sensor-ids
${KAFKA_DIR}/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic allowed-sensor-ids-keyed

# add list of allowed sensors
${KAFKA_DIR}/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic allowed-sensor-ids < allowed_sensors
