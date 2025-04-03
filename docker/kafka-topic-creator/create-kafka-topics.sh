#!/bin/bash

# SPDX-License-Identifier: Apache-2.0
# Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin Programme.
#
# Copyright (c) Telicent Ltd.
#
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
#
# Modifications made by the National Digital Twin Programme (NDTP)
# © Crown Copyright 2025. This work has been developed

# Parse string of kafka topics into an array
# https://stackoverflow.com/a/10586169/4587961
kafkatopicsArrayString="$KAFKA_TOPICS"
IFS=' ' read -r -a kafkaTopicsArray <<< "$kafkatopicsArrayString"

# A separate variable for kafka host.
bootstrapserverValue=$BOOTSTRAP_VALUE

# Create kafka topic for each topic item from split array of topics.
for newTopic in "${kafkaTopicsArray[@]}"; do
  # https://kafka.apache.org/quickstart
  kafka-topics --create --topic "$newTopic" --partitions 1 --replication-factor 1 --if-not-exists --bootstrap-server "$bootstrapserverValue"
  echo "Created topic - $newTopic"
  sleep 2.0s
done
