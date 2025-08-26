// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

/*
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

package uk.gov.dbt.ndtp.federator.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.sinks.KafkaSink;

public class GRPCClientTest {
    private static final String SERVER_NAME = "TEST_SERVER_NAME";
    private static final String TOPIC_PREFIX = "TEST_TOPIC_PREFIX";
    private static final String TOPIC_NAME = "TEST_TOPIC_NAME";

    @BeforeAll
    static void beforeAll() {
        TestPropertyUtil.clearProperties();
        TestPropertyUtil.setUpProperties("client.properties");
    }

    @AfterAll
    static void afterAll() {
        TestPropertyUtil.clearProperties();
    }

    @Test
    void getRedisPrefix() {
        String clientName = RandomStringUtils.random(10);
        String serverName = RandomStringUtils.random(10);

        try (GRPCClient client = new GRPCClient(clientName, "key", serverName, "host", 1, false, TOPIC_PREFIX)) {
            String prefix = client.getRedisPrefix();

            assertEquals(clientName + "-" + serverName, prefix);
        }
    }

    @Test
    void getSenderWithTopicPrefix() {
        try (MockedStatic<KafkaUtil> kafka = mockStatic(KafkaUtil.class)) {
            kafka.when(() -> KafkaUtil.getKafkaSink(any())).thenReturn(mock(KafkaSink.class));
            GRPCClient.getSender(TOPIC_NAME, TOPIC_PREFIX, SERVER_NAME);
            kafka.verify(() -> KafkaUtil.getKafkaSink("TEST_TOPIC_PREFIX-TEST_SERVER_NAME-TEST_TOPIC_NAME"));
        }
    }

    @Test
    void getSenderWithoutTopicPrefix() {
        try (MockedStatic<KafkaUtil> kafka = mockStatic(KafkaUtil.class)) {
            kafka.when(() -> KafkaUtil.getKafkaSink(any())).thenReturn(mock(KafkaSink.class));
            GRPCClient.getSender(TOPIC_NAME, "", SERVER_NAME);
            kafka.verify(() -> KafkaUtil.getKafkaSink("TEST_SERVER_NAME-TEST_TOPIC_NAME"));
        }
    }
}
