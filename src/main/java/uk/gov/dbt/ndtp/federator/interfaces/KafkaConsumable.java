// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.interfaces;

import java.util.List;

/**
 * Gets the topics to be read and processes out the data for an offset.
 */
public interface KafkaConsumable extends AutoCloseable {

    /**
     * Returns the prefix to be used when storing offsets in Redis
     * @return the prefix
     */
    String getRedisPrefix();

    /**
     * Gets the list of Kafka Topics to read from.
     *
     * @return Topics to read from
     */
    List<String> obtainTopics();

    /**
     * for a specific topic read all the new data from a stated offset.
     *
     * @param topic  to read from
     * @param offset to start reading from
     */
    void processTopic(String topic, long offset);

    /**
     * Simple method for testing connectivity to Kafka
     */
    void testConnectivity();
}
