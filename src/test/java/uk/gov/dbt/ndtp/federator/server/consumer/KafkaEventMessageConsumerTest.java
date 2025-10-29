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

package uk.gov.dbt.ndtp.federator.server.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static uk.gov.dbt.ndtp.federator.common.utils.TestPropertyUtil.setUpProperties;

import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.common.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;

class KafkaEventMessageConsumerTest {

    private static final KafkaEventSource.Builder mockKafkaBuilder = mock(KafkaEventSource.Builder.class);

    private static final KafkaEventSource mockEventSource = mock(KafkaEventSource.class);

    private static final MockedStatic<KafkaUtil> mockedKafkaUtil = Mockito.mockStatic(KafkaUtil.class);

    private static final String TOPIC = "TOPIC";
    private static final String CLIENT_ID = "CLIENT-ID";
    private static final long OFFSET = 1L;

    @AfterAll
    static void afterAll() {
        mockedKafkaUtil.close();
    }

    @BeforeEach
    void setupTests() {
        setUpProperties();
        // Reset shared mocks to avoid cross-test leakage of interactions and state
        reset(mockKafkaBuilder, mockEventSource);
        mockedKafkaUtil.when(KafkaUtil::getKafkaSourceBuilder).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.keyDeserializer(StringDeserializer.class)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.valueDeserializer(StringDeserializer.class)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.topic("TOPIC")).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.consumerGroup(anyString())).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.readPolicy(any())).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.build()).thenReturn(mockEventSource);
        when(mockEventSource.isClosed()).thenReturn(false);
        doNothing().when(mockEventSource).close();
    }

    MessageConsumer<KafkaEvent<String, String>> getConsumer() {
        return new KafkaEventMessageConsumer<>(
                StringDeserializer.class, StringDeserializer.class, TOPIC, OFFSET, CLIENT_ID);
    }

    @Test
    public void test_getNextMessage() {
        // given
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(TOPIC, 0, OFFSET, null, null);
        KafkaEvent<String, String> kafkaEvent = new KafkaEvent<>(consumerRecord, mockEventSource);
        when(mockEventSource.poll(any(Duration.class))).thenReturn(kafkaEvent);
        MessageConsumer<KafkaEvent<String, String>> consumer = getConsumer();
        // when
        KafkaEvent<String, String> actual = consumer.getNextMessage();
        // then
        assertEquals(kafkaEvent, actual);
    }

    @Test
    void stillAvailable_returns_false_and_closes_on_inactivity_timeout_zero() {
        // ensure no interference from other tests using the same mock
        reset(mockEventSource);
        when(mockEventSource.isClosed()).thenReturn(false);
        doNothing().when(mockEventSource).close();

        try (MockedStatic<PropertyUtil> mockedProps = Mockito.mockStatic(PropertyUtil.class)) {
            // Make both pollDuration and inactivityTimeout be zero to trigger immediate timeout on first check
            mockedProps
                    .when(() -> PropertyUtil.getPropertyDurationValue(anyString(), anyString()))
                    .thenReturn(Duration.ZERO);

            KafkaEventMessageConsumer<String, String> consumer = new KafkaEventMessageConsumer<>(
                    StringDeserializer.class, StringDeserializer.class, "TOPIC", 0L, "CLIENT");

            boolean available = consumer.stillAvailable();

            assertFalse(available, "Consumer should report unavailable due to immediate inactivity timeout");
            verify(mockEventSource, times(1)).close();
        }
    }

    @Test
    void close_delegates_to_underlying_source() {
        KafkaEventMessageConsumer<String, String> consumer = new KafkaEventMessageConsumer<>(
                StringDeserializer.class, StringDeserializer.class, "TOPIC", 0L, "CLIENT");

        consumer.close();

        verify(mockEventSource, times(1)).close();
    }
}
