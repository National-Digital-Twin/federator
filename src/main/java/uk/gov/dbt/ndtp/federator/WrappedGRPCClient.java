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
package uk.gov.dbt.ndtp.federator;

import uk.gov.dbt.ndtp.federator.client.grpc.GRPCTopicClient;
import uk.gov.dbt.ndtp.federator.client.interfaces.KafkaConsumable;

/**
 * Wraps the GRPC Client implementation and exposes the functionality through
 * the KafkaConsumable interface
 */
public class WrappedGRPCClient implements KafkaConsumable {

    private final GRPCTopicClient client;

    /**
     * Creates the WrapppedGRPCClient from the provided GRPC Client
     *
     * @param client This gets wrapped by the interface implementation.
     */
    public WrappedGRPCClient(GRPCTopicClient client) {
        this.client = client;
    }

    @Override
    public void close() throws Exception {
        this.client.close();
    }

    @Override
    public String getRedisPrefix() {
        return this.client.getRedisPrefix();
    }

    @Override
    public void processTopic(String topic, long offset) {
        this.client.processTopic(topic, offset);
    }

    @Override
    public void testConnectivity() {
        this.client.testConnectivity();
    }
}
