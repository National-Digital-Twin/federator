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

package uk.gov.dbt.ndtp.federator.client.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.common.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.sinks.KafkaSink;

class GRPCTopicClientTest {
    private static final String SERVER_NAME = "TEST_SERVER_NAME";
    private static final String TOPIC_PREFIX = "TEST_TOPIC_PREFIX";
    private static final String TOPIC_NAME = "TEST_TOPIC_NAME";

    @BeforeAll
    static void beforeAll() {
        PropertyUtil.clear();
        PropertyUtil.init("client.properties");
    }

    @AfterAll
    static void afterAll() {
        PropertyUtil.clear();
    }

    @Test
    void getRedisPrefix()  {
        String clientName = RandomStringUtils.insecure().nextAlphabetic(10);
        String serverName = RandomStringUtils.insecure().nextAlphabetic(10);

        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);

        try (GRPCTopicClient client =
                new GRPCTopicClient(clientName, "key", serverName, "host", 1, false, TOPIC_PREFIX) {
                    @Override
                    protected ManagedChannel buildChannel(String host, int port, boolean isTls) {
                        return channel;
                    }
                }) {
            String prefix = client.getRedisPrefix();
            assertEquals(clientName + "-" + serverName, prefix);
        }
    }

    @Test
    void getSenderWithTopicPrefix() {
        try (MockedStatic<KafkaUtil> kafka = mockStatic(KafkaUtil.class)) {
            kafka.when(() -> KafkaUtil.getKafkaSink(any())).thenReturn(mock(KafkaSink.class));
            GRPCTopicClient.getSender(TOPIC_NAME, TOPIC_PREFIX, SERVER_NAME);
            kafka.verify(() -> KafkaUtil.getKafkaSink("TEST_TOPIC_PREFIX-TEST_SERVER_NAME-TEST_TOPIC_NAME"));
        }
    }

    @Test
    void getSenderWithoutTopicPrefix() {
        try (MockedStatic<KafkaUtil> kafka = mockStatic(KafkaUtil.class)) {
            kafka.when(() -> KafkaUtil.getKafkaSink(any())).thenReturn(mock(KafkaSink.class));
            GRPCTopicClient.getSender(TOPIC_NAME, "", SERVER_NAME);
            kafka.verify(() -> KafkaUtil.getKafkaSink("TEST_SERVER_NAME-TEST_TOPIC_NAME"));
        }
    }

    @Test
    void constructor_tls_uses_generateChannel_with_tls_flag_true() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);

        final String host = "secure-host";
        final int port = 8443;

        class TestClient extends GRPCTopicClient {
            String seenHost;
            int seenPort;
            boolean seenTls;

            TestClient() {
                super("client", "key", "server", host, port, true, "pref");
            }

            @Override
            protected ManagedChannel buildChannel(String h, int p, boolean tls) {
                this.seenHost = h;
                this.seenPort = p;
                this.seenTls = tls;
                return channel;
            }
        }

        TestClient client = new TestClient();
        assertEquals(host, client.seenHost);
        assertEquals(port, client.seenPort);
        assertEquals(true, client.seenTls);
        client.close();
    }

    @Test
    void constructor_nonTls_uses_generateChannel_and_close_shuts_down_channel() throws Exception {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);

        // Use the protected pass-through constructor to inject our mock channel
        GRPCTopicClient client = new GRPCTopicClient("client", "key", "server", "pref", channel) {};

        // When closing, ensure the underlying channel is shutdown
        client.close();
        verify(channel).shutdown();
        verify(channel).awaitTermination(5, TimeUnit.SECONDS);
    }
}
