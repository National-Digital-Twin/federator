#!/bin/bash

# SPDX-License-Identifier: Apache-2.0
# © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
# and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

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

# Script to bulk load test data from a file into kafka topics
# Parameters:
#   - KAFKA_SERVERS: Kafka servers to connect to
#   - KAFKA_TOPICS: Kafka topics to create
#   - DATA_FILENAME: Data files to load into kafka topics
#   - DATA_DELIMITER: Separator for data used within the data file
#   - DELAY_BETWEEN_DATA_LOADS: Delay between each time the file is sent to kafka
#   - NUMBER_OF_DATA_LOADS: Total number of times to load the data file into kafka

# Currently only supports loading data from a file into kafka topics for a specified number of times
# The data file should be in the input/knowledge directory
# TODO - Add support for loading data from multiple data files

#Kafka quickstart guide: https://kafka.apache.org/quickstart

#Script starts here
#Turn on debugging
#set -x

#echo out some debug statements
echo "--> Start of ${0} with the following input parameters:"
echo "---> KAFKA_SERVERS: ${KAFKA_SERVERS}"
echo "---> KAFKA_TOPICS: ${KAFKA_TOPICS}"
echo "---> DATA_FILENAME: ${DATA_FILENAME}"
echo "---> DELAY_BETWEEN_DATA_LOADS: ${DELAY_BETWEEN_DATA_LOADS}"
echo "---> NUMBER_OF_DATA_LOADS: ${NUMBER_OF_DATA_LOADS}"
echo "---> DATA_DELIMITER: ${DATA_DELIMITER}"
echo

if [ "${NUMBER_OF_DATA_LOADS}" -eq 0 ]; then
  echo "--> NUMBER_OF_DATA_LOADS is set to 0, exiting script"
  exit 0
fi

#Take the string of kafka topics and split it into an array of topics
IFS=' ' read -r -a kafkaTopicsArray <<< "$KAFKA_TOPICS"
#Take the string of data files and split it into an array of data files (currently only supports one data file)
IFS=' ' read -r -a dataFilenameArray <<< "$DATA_FILENAME"
#Take the string of kafka servers and split it into an array of servers
IFS=' ' read -r -a kafkaServersArray <<< "$KAFKA_SERVERS"

#For Debugging
#echo "kafkaTopicsArray: ${kafkaTopicsArray[*]}"
#echo "dataFilenameArray: ${dataFilenameArray[*]}"
#echo "kafkaServersArray: ${kafkaServersArray[*]}"

# Create kafka topicToCreate for each topicToCreate item

#For each kafka broker server create the topics if they do not exist
for kafkaServer in "${kafkaServersArray[@]}"; do
  for topicToCreate in "${kafkaTopicsArray[@]}"; do
    kafka-topics --create --topic "${topicToCreate}" --partitions 1 --replication-factor 1 --if-not-exists --bootstrap-server "${kafkaServer}"
  done
done

#Count the number of records within the data file to be loaded
numberOfRecordsInDataFile=$(wc -l < ./input/knowledge/"${dataFilenameArray[0]}")

# Load data into each topic held within kafkaTopicsArray[] for the number of times specified in the NUMBER_OF_DATA_LOADS parameter
currentDataLoad=0
  while [ $currentDataLoad -lt "$NUMBER_OF_DATA_LOADS" ]; do
    echo "--> Current data load: $currentDataLoad of a total of $NUMBER_OF_DATA_LOADS"
    for kafkaServer in "${kafkaServersArray[@]}"; do #For each kafka server in the array
      for topicToLoad in "${kafkaTopicsArray[@]}"; do  #For each topic in the array
        kafka-console-producer --bootstrap-server "${kafkaServer}" --topic "${topicToLoad}" --property parse.headers=true --property headers.separator="${DATA_DELIMITER}" < ./input/knowledge/"${dataFilenameArray[0]}"
        echo "----> Kafka server ${kafkaServer} Topic ${topicToLoad} was loaded with data from file ${dataFilenameArray[0]} with ${numberOfRecordsInDataFile} records"
      done
    done
   ((currentDataLoad+=1))
   echo "---> Sleeping for ${DELAY_BETWEEN_DATA_LOADS}....."
   sleep "${DELAY_BETWEEN_DATA_LOADS}"
done
echo "--> Finished loading data into kafka topics"
echo "--> End of ${0}"
exit 0
