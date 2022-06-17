#!/bin/sh

if [ "${KAFKA_DIR}" == "" ]; then
  KAFKA_DIR=../../kafka_2.13-3.2.0
fi

# create topics
${KAFKA_DIR}/bin/kafka-topics.sh --create --bootstrap-server localhost:9092  --replication-factor 1 --partitions 1 --topic allowed-sensor-ids
${KAFKA_DIR}/bin/kafka-topics.sh --create --bootstrap-server localhost:9092  --replication-factor 1 --partitions 1 --topic allowed-sensor-ids-keyed

# add list of allowed sensors
${KAFKA_DIR}/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic allowed-sensor-ids < allowed_sensors
