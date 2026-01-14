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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Iterator;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.common.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.RetryableException;
import uk.gov.dbt.ndtp.grpc.FederatorServiceGrpc;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;
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
    void getRedisPrefix() {
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
    void testConnectivity_coverage() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);

        GRPCTopicClient client = new GRPCTopicClient("c", "k", "s", "p", channel);
        client.testConnectivity();
        client.close();
    }

    @Test
    void processTopic_logic_coverage() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);
        KafkaSink<Bytes, Bytes> sink = mock(KafkaSink.class);
        RedisUtil redis = mock(RedisUtil.class);

        try (MockedStatic<KafkaUtil> kafkaMock = mockStatic(KafkaUtil.class);
                MockedStatic<RedisUtil> redisMock = mockStatic(RedisUtil.class)) {

            kafkaMock.when(() -> KafkaUtil.getKafkaSink(anyString())).thenReturn(sink);
            redisMock.when(RedisUtil::getInstance).thenReturn(redis);

            GRPCTopicClient client = spy(new GRPCTopicClient("client", "key", "server", "pref", channel));
            doNothing().when(client).consumeMessagesAndSendOn(any(), any());

            client.processTopic("topic", 100L);

            verify(client).consumeMessagesAndSendOn(any(TopicRequest.class), eq(sink));
            client.close();
        }
    }

    @Test
    void processTopic_statusRuntimeException_invalidArgument() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);
        KafkaSink<Bytes, Bytes> sink = mock(KafkaSink.class);

        try (MockedStatic<KafkaUtil> kafkaMock = mockStatic(KafkaUtil.class);
                MockedStatic<RedisUtil> redisMock = mockStatic(RedisUtil.class)) {

            kafkaMock.when(() -> KafkaUtil.getKafkaSink(anyString())).thenReturn(sink);
            redisMock.when(RedisUtil::getInstance).thenReturn(mock(RedisUtil.class));

            GRPCTopicClient client = spy(new GRPCTopicClient("client", "key", "server", "pref", channel));
            doThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT))
                    .when(client)
                    .consumeMessagesAndSendOn(any(), any());

            assertThrows(StatusRuntimeException.class, () -> client.processTopic("topic", 100L));
            client.close();
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void processTopic_statusRuntimeException_other() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);
        KafkaSink<Bytes, Bytes> sink = mock(KafkaSink.class);

        try (MockedStatic<KafkaUtil> kafkaMock = mockStatic(KafkaUtil.class);
                MockedStatic<RedisUtil> redisMock = mockStatic(RedisUtil.class)) {

            kafkaMock.when(() -> KafkaUtil.getKafkaSink(anyString())).thenReturn(sink);
            redisMock.when(RedisUtil::getInstance).thenReturn(mock(RedisUtil.class));

            GRPCTopicClient client = spy(new GRPCTopicClient("client", "key", "server", "pref", channel));
            doThrow(new StatusRuntimeException(Status.INTERNAL)).when(client).consumeMessagesAndSendOn(any(), any());

            assertThrows(StatusRuntimeException.class, () -> client.processTopic("topic", 100L));
            client.close();
        }
    }

    @Test
    void processTopic_kafkaException() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);

        try (MockedStatic<KafkaUtil> kafkaMock = mockStatic(KafkaUtil.class);
                MockedStatic<RedisUtil> redisMock = mockStatic(RedisUtil.class)) {

            kafkaMock.when(() -> KafkaUtil.getKafkaSink(anyString())).thenThrow(new KafkaException("error"));
            redisMock.when(RedisUtil::getInstance).thenReturn(mock(RedisUtil.class));

            GRPCTopicClient client = new GRPCTopicClient("client", "key", "server", "pref", channel);
            assertThrows(RetryableException.class, () -> client.processTopic("topic", 100L));
            client.close();
        }
    }

    @Test
    void sendMessage_coverage() {
        KafkaSink<Bytes, Bytes> sink = mock(KafkaSink.class);
        KafkaByteBatch batch = KafkaByteBatch.newBuilder()
                .setKey(com.google.protobuf.ByteString.copyFromUtf8("key"))
                .setValue(com.google.protobuf.ByteString.copyFromUtf8("value"))
                .addShared(uk.gov.dbt.ndtp.grpc.Headers.newBuilder()
                        .setKey("hk")
                        .setValue("hv")
                        .build())
                .build();

        GRPCTopicClient.sendMessage(sink, batch);
        verify(sink).send(any());
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void consumeMessagesAndSendOn_coverage() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);
        KafkaSink<Bytes, Bytes> sink = mock(KafkaSink.class);
        RedisUtil redis = mock(RedisUtil.class);

        try (MockedStatic<RedisUtil> redisMock = mockStatic(RedisUtil.class);
                MockedStatic<PropertyUtil> propertyMock = mockStatic(PropertyUtil.class)) {
            redisMock.when(RedisUtil::getInstance).thenReturn(redis);
            propertyMock
                    .when(() -> PropertyUtil.getPropertyIntValue(anyString(), anyString()))
                    .thenReturn(1);

            FederatorServiceGrpc.FederatorServiceBlockingStub stub =
                    mock(FederatorServiceGrpc.FederatorServiceBlockingStub.class);
            GRPCTopicClient client = new GRPCTopicClient("client", "key", "server", "pref", channel) {
                @Override
                protected FederatorServiceGrpc.FederatorServiceBlockingStub getStub() {
                    return stub;
                }
            };

            KafkaByteBatch batch = KafkaByteBatch.newBuilder()
                    .setTopic("topic")
                    .setOffset(100L)
                    .setKey(com.google.protobuf.ByteString.copyFromUtf8("k"))
                    .setValue(com.google.protobuf.ByteString.copyFromUtf8("v"))
                    .build();

            Iterator<KafkaByteBatch> iterator = mock(Iterator.class);
            when(iterator.hasNext()).thenReturn(true, false);
            // Return batch once, then return null to signify end of stream or time out
            when(iterator.next()).thenReturn(batch).thenReturn(null);
            when(stub.getKafkaConsumer(any())).thenReturn(iterator);

            TopicRequest req =
                    TopicRequest.newBuilder().setTopic("topic").setOffset(100L).build();
            client.consumeMessagesAndSendOn(req, sink);

            verify(redis).setOffset(anyString(), eq("topic"), eq(101L));
            client.close();
        }
    }

    @Test
    void consumeMessagesAndSendOn_exception() {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);
        GRPCTopicClient client = new GRPCTopicClient("client", "key", "server", "pref", channel);
        assertThrows(NullPointerException.class, () -> client.consumeMessagesAndSendOn(null, null));
        client.close();
    }

    @Test
    void testConcatCompoundTopicName() {
        String result = GRPCTopicClient.concatCompoundTopicName("topic", "prefix", "server");
        assertEquals("prefix-server-topic", result);

        result = GRPCTopicClient.concatCompoundTopicName("topic", "", "server");
        assertEquals("server-topic", result);

        result = GRPCTopicClient.concatCompoundTopicName("topic", null, "server");
        assertEquals("server-topic", result);
    }

    @Test
    void constructor_with_connectionProperties() {
        ConnectionProperties props = mock(ConnectionProperties.class);
        when(props.clientName()).thenReturn("c");
        when(props.clientKey()).thenReturn("k");
        when(props.serverName()).thenReturn("s");
        when(props.serverHost()).thenReturn("h");
        when(props.serverPort()).thenReturn(1234);
        when(props.tls()).thenReturn(false);

        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);

        class TestClient extends GRPCTopicClient {
            TestClient() {
                super(props, "p");
            }

            @Override
            protected ManagedChannel buildChannel(String h, int p, boolean tls) {
                return channel;
            }
        }
        TestClient client = new TestClient();
        assertEquals("p", client.topicPrefix);
        client.close();
    }
}
