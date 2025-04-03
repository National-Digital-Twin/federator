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
package uk.gov.dbt.ndtp.federator.consumer;

/**
 * Represents the offsets of a client for a given topic.
 * This is used to track the progress of a client.
 */
public class ClientTopicOffsets {

    private final String client;
    private final String topic;
    private final long offset;

    public ClientTopicOffsets(String client, String topic, long offset) {
        this.client = client;
        this.topic = topic;
        this.offset = offset;
    }

    public String getClient() {
        return client;
    }

    public String getTopic() {
        return topic;
    }

    public long getOffset() {
        return offset;
    }
}
