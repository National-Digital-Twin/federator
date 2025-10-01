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

package uk.gov.dbt.ndtp.federator.conductor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil.clearProperties;
import static uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil.setUpProperties;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.federator.grpc.LimitedServerCallStreamObserver;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.secure.agent.payloads.RdfPayload;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.serializers.RdfPayloadDeserializer;

public class RdfMessageConductorTest {

    private static final String TOPIC = UUID.randomUUID().toString().substring(0, 6);
    private static final String CLIENT_ID = UUID.randomUUID().toString().substring(0, 6);
    private static final long OFFSET = new Random().nextLong();
    private static final ClientTopicOffsets topicData = new ClientTopicOffsets(CLIENT_ID, TOPIC, OFFSET);
    private static final KafkaEventSource.Builder mockKafkaBuilder = mock(KafkaEventSource.Builder.class);
    private static final KafkaEventSource mockEventSource = mock(KafkaEventSource.class);
    private static final MockedStatic<KafkaUtil> mockedKafkaUtil = Mockito.mockStatic(KafkaUtil.class);
    private static final MessageFilter<KafkaEvent<?, ?>> mockFilter = mock(MessageFilter.class);
    private static final Set<String> emptySharedHeaders = Set.of();

    static {
        setUpProperties();
    }

    private final StreamObservable mockObserver = mock(LimitedServerCallStreamObserver.class);
    private RdfMessageConductor cut;

    @BeforeAll
    public static void setupTests() {
        setUpProperties();
        mockedKafkaUtil.when(KafkaUtil::getKafkaSourceBuilder).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.keyDeserializer(eq(StringDeserializer.class))).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.valueDeserializer(eq(RdfPayloadDeserializer.class)))
                .thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.topic(TOPIC)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.consumerGroup(CLIENT_ID)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.readPolicy(KafkaUtil.getReadPolicy(OFFSET))).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.build()).thenReturn(mockEventSource);
    }

    @AfterAll
    public static void clearDown() {
        clearProperties();
        mockedKafkaUtil.close();
    }

    @AfterEach
    public void resetMocks() {
        reset(mockObserver, mockEventSource);
    }

    @Test
    void test_continueProcessing_whenCancelled() {
        // given
        cut = new RdfMessageConductor(
                topicData, mockObserver, List.of(new AttributesDTO("foo", "bar", "String")), emptySharedHeaders);
        when(mockObserver.isCancelled()).thenReturn(true);
        // when
        boolean actual = cut.continueProcessing();
        // then
        assertFalse(actual);
    }

    @Test
    void test_continueProcessing_happyPath() {
        // given
        cut = new RdfMessageConductor(topicData, mockObserver, List.of(), emptySharedHeaders);

        when(mockObserver.isCancelled()).thenReturn(false);
        // when
        boolean actual = cut.continueProcessing();
        // then
        assertTrue(actual);
    }

    @Test
    void test_processMessages_happyPath_nullMessage() {
        // given
        when(mockEventSource.isClosed()).thenReturn(false).thenReturn(true);
        when(mockEventSource.poll(any())).thenReturn(null);
        cut = new RdfMessageConductor(
                topicData, mockObserver, List.of(new AttributesDTO("foo", "bar", "String")), emptySharedHeaders);
        // when
        cut.processMessages();
        // then
        verify(mockObserver, never()).onNext(any());
    }

    @Test
    void test_processMessages_happyPath_filteredOutMessage() throws LabelException {
        // given
        KafkaEvent<String, RdfPayload> message =
                new KafkaEvent<>(new ConsumerRecord<>("topic", 1, 1, "key", null), null);
        when(mockEventSource.isClosed()).thenReturn(false).thenReturn(true);
        when(mockEventSource.poll(any())).thenReturn(message);
        // Set a filter that will not match the message (e.g., header "foo" = "bar")
        List<AttributesDTO> filterAttributes = List.of(new AttributesDTO("foo", "bar", "String"));
        cut = new RdfMessageConductor(topicData, mockObserver, filterAttributes, emptySharedHeaders);
        // when
        cut.processMessages();
        // then
        verify(mockObserver, never()).onNext(any());
    }

    @Test
    void test_close_happyPath() {
        // given
        cut = new RdfMessageConductor(
                topicData, mockObserver, List.of(new AttributesDTO("foo", "bar", "String")), emptySharedHeaders);
        // when
        cut.close();
        // then
        verify(mockEventSource).close();
    }
}
