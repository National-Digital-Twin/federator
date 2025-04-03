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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil.setUpProperties;

import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;

public class KafkaEventMessageConsumerTest extends MessageConsumerTest<KafkaEvent<String, String>> {

    private static final KafkaEventSource.Builder mockKafkaBuilder = mock(KafkaEventSource.Builder.class);

    private static final KafkaEventSource mockEventSource = mock(KafkaEventSource.class);

    private static final MockedStatic<KafkaUtil> mockedKafkaUtil = Mockito.mockStatic(KafkaUtil.class);

    private static final String TOPIC = "TOPIC";
    private static final String CLIENT_ID = "CLIENT-ID";
    private static final long OFFSET = 1L;

    @BeforeAll
    public static void setupTests() {
        setUpProperties();
        mockedKafkaUtil.when(KafkaUtil::getKafkaSourceBuilder).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.keyDeserializer(eq(StringDeserializer.class))).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.valueDeserializer(eq(StringDeserializer.class))).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.topic(TOPIC)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.consumerGroup(CLIENT_ID)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.readPolicy(KafkaUtil.getReadPolicy(OFFSET))).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.build()).thenReturn(mockEventSource);
        when(mockEventSource.isClosed()).thenReturn(false).thenReturn(true);
    }

    @AfterAll
    public static void cleardownTests() {
        mockedKafkaUtil.close();
    }

    @Override
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
}
