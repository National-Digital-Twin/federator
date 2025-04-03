#!/bin/bash

# SPDX-License-Identifier: Apache-2.0
# Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin Programme.

# Copyright (c) Telicent Ltd.

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Modifications made by the National Digital Twin Programme (NDTP)
# © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
# and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

#uncomment below to debug the script
#set +x

# This script creates kafka topics and populates them with data.
# Reads the environment variables KNOWLEDGE_TOPIC and KNOWLEDGE_DATA from the yml file
# The KNOWLEDGE_TOPIC is a string of kafka topics separated by space.
# The KNOWLEDGE_DATA is a string of data files separated by space.
# The script uses the KAFKA_BROKER_SERVER environment variable to connect to the kafka broker to create topics and populate data.
# The script uses the data separator value from the environment variable HEADER_SEPARATOR.
# The script uses the input data from the input/knowledge directory.

## https://kafka.apache.org/quickstart

#echo out the properties for debugging
echo "KAFKA_BROKER_SERVER: $KAFKA_BROKER_SERVER"
echo "KNOWLEDGE_TOPIC: $KNOWLEDGE_TOPIC"
echo "KNOWLEDGE_DATA: $KNOWLEDGE_DATA"
echo "DATA_DELIMITER: $DATA_DELIMITER"

# Create kafka topics from the environment variable.
# Split the string of kafka topics into an array (https://stackoverflow.com/a/10586169/4587961)
IFS=' ' read -r -a kafkaTopicsArray <<< "$KNOWLEDGE_TOPIC"

# Create kafka topic for each topic item from split array of topics.
for newTopic in "${kafkaTopicsArray[@]}"; do
  kafka-topics --create --topic "$newTopic" --partitions 1 --replication-factor 1 --if-not-exists --bootstrap-server "$KAFKA_BROKER_SERVER"
  echo "Created topic - $newTopic"
done

# A separate variable for kafka host.
IFS=' ' read -r -a knowledgeTopicsArray <<< "$KNOWLEDGE_TOPIC"
IFS=' ' read -r -a knowledgeDatasArray <<< "$KNOWLEDGE_DATA"

count=0
# Populate kafka topics for each topic item from split array of topics.
for knowledgeTopic in "${knowledgeTopicsArray[@]}"; do
  echo "Checking topic exists - $knowledgeTopic"
  kafka-topics --bootstrap-server "$KAFKA_BROKER_SERVER" --describe --topic "$knowledgeTopic"
  topic_exists=$?
  if [ ${topic_exists} -eq 0 ]; then
    echo "Loading knowledge topic data ${knowledgeDatasArray[${count}]} into topic ${knowledgeTopic}"
    kafka-console-producer --bootstrap-server "$KAFKA_BROKER_SERVER" --topic "$knowledgeTopic" --property parse.headers=true --property headers.separator="${DATA_DELIMITER}" < ./input/knowledge/"${knowledgeDatasArray[${count}]}"
  else
    echo "Topic ${knowledgeTopic} does not exists - skipping"
  fi
  ((count+=1))
done
