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

echo "--> Start of ${0} with the following input parameters:"
echo "---> KAFKA_BROKER_SERVER: ${KAFKA_BROKER_SERVER}"
echo "---> TOPICS_TO_CHECK: ${TOPICS_TO_CHECK}"
echo "---> EXPECTED_MESSAGE_COUNTS: ${EXPECTED_MESSAGE_COUNTS}"
echo "---> DELAY_BEFORE_CONSUMING_DATA: ${DELAY_BEFORE_CONSUMING_DATA}"
echo

# Read topics and expected counts from environment variables
IFS=' ' read -r -a topics <<< "$TOPICS_TO_CHECK"
IFS=' ' read -r -a expected_counts <<< "$EXPECTED_MESSAGE_COUNTS"

# Check if both arrays match in size
if [[ ${#topics[@]} -ne ${#expected_counts[@]} ]]; then
  echo "Error: Number of topics and expected counts do not match!"
  exit 1
fi
# Sleep briefly to ensure messages are processed
sleep "${DELAY_BEFORE_CONSUMING_DATA}"

# Loop through topics and check message counts
for index in "${!topics[@]}"; do
  topic="${topics[$index]}"
  expected="${expected_counts[$index]}"

  # Get message count
  message_count=$(kafka-run-class kafka.tools.GetOffsetShell --broker-list "$KAFKA_BROKER_SERVER" --topic "$topic" --time -1 | awk -F ":" '{sum += $3} END {print sum}')

  # If message count is empty, set to 0
  message_count=${message_count:-0}

  # Compare expected vs actual
  if [[ "$message_count" -eq "$expected" ]]; then
    echo "✅ Topic '$topic' has $message_count messages (as expected)."
  else
    echo "❌ WARNING: Topic '$topic' has $message_count messages, expected $expected!"
    exit 1  # Fails Docker Compose
  fi
done
echo "--> Finished verifying the number of messages"
echo "--> End of ${0}"
exit 0
