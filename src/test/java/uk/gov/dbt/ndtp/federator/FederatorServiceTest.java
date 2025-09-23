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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.service.ProducerConsumerConfigService;
import uk.gov.dbt.ndtp.federator.utils.ProducerConsumerConfigServiceFactory;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

class FederatorServiceTest {

    private static final Set<String> EMPTY_SHARED_HEADERS = Set.of();

    // -------------------- Helper reflection methods --------------------

    private List<AttributesDTO> invokeGetFilterAttributesForConsumer(
            FederatorService cut, String consumerId, String topic, ProducerConfigDTO cfg) {
        try {
            Method m = FederatorService.class.getDeclaredMethod(
                    "getFilterAttributesForConsumer", String.class, String.class, ProducerConfigDTO.class);
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<AttributesDTO> result = (List<AttributesDTO>) m.invoke(cut, consumerId, topic, cfg);
            return result;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean invokeHasConsumerAccessToTopic(
            FederatorService cut, String consumerId, String topic, ProducerConfigDTO cfg) {
        try {
            Method m = FederatorService.class.getDeclaredMethod(
                    "hasConsumerAccessToTopic", String.class, String.class, ProducerConfigDTO.class);
            m.setAccessible(true);
            return (boolean) m.invoke(cut, consumerId, topic, cfg);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private ProducerConfigDTO buildConfig(String topic, String consumerId, List<AttributesDTO> attrs) {
        ConsumerDTO cons = ConsumerDTO.builder().idpClientId(consumerId).build();
        if (attrs != null) {
            cons.getAttributes().addAll(attrs);
        }
        ProductDTO product =
                ProductDTO.builder().topic(topic).consumers(new ArrayList<>()).build();
        product.getConsumers().add(cons);
        ProducerDTO producer = ProducerDTO.builder().products(new ArrayList<>()).build();
        producer.getProducts().add(product);
        return ProducerConfigDTO.builder().producers(List.of(producer)).build();
    }

    // -------------------- Tests for getFilterAttributesForConsumer --------------------

    @Test
    void test_getFilterAttributesForConsumer_returnsAttributesForMatchingConsumerAndTopic() {
        // given
        FederatorService cut = new FederatorService(EMPTY_SHARED_HEADERS);
        List<AttributesDTO> attrs = List.of(new AttributesDTO("tenant", "alpha", "String"));
        ProducerConfigDTO cfg = buildConfig("telemetry.raw", "client-a", attrs);
        // when
        List<AttributesDTO> result = invokeGetFilterAttributesForConsumer(cut, "client-a", "telemetry.raw", cfg);
        // then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("tenant", result.get(0).getName());
        assertEquals("alpha", result.get(0).getValue());
    }

    @Test
    void test_getFilterAttributesForConsumer_returnsEmpty_whenNoMatchOrNulls() {
        FederatorService cut = new FederatorService(EMPTY_SHARED_HEADERS);
        // null config
        assertEquals(Collections.emptyList(), invokeGetFilterAttributesForConsumer(cut, "x", "y", null));
        // config but different topic
        ProducerConfigDTO cfg1 = buildConfig("topic-1", "client-a", List.of(new AttributesDTO("k", "v", null)));
        assertTrue(invokeGetFilterAttributesForConsumer(cut, "client-a", "topic-2", cfg1)
                .isEmpty());
        // config but different consumer
        ProducerConfigDTO cfg2 = buildConfig("topic-1", "client-b", List.of(new AttributesDTO("k", "v", null)));
        assertTrue(invokeGetFilterAttributesForConsumer(cut, "client-a", "topic-1", cfg2)
                .isEmpty());
    }

    // -------------------- Tests for hasConsumerAccessToTopic --------------------

    @Test
    void test_hasConsumerAccessToTopic_trueWhenMatching() {
        FederatorService cut = new FederatorService(EMPTY_SHARED_HEADERS);
        ProducerConfigDTO cfg = buildConfig("dp1", "CLIENT-123", null);
        assertTrue(invokeHasConsumerAccessToTopic(cut, "client-123", "dp1", cfg));
    }

    @Test
    void test_hasConsumerAccessToTopic_falseWhenNoProducersOrNoMatch() {
        FederatorService cut = new FederatorService(EMPTY_SHARED_HEADERS);
        // null config
        assertFalse(invokeHasConsumerAccessToTopic(cut, "c", "t", null));
        // empty producers
        ProducerConfigDTO cfgEmpty = ProducerConfigDTO.builder().producers(null).build();
        assertFalse(invokeHasConsumerAccessToTopic(cut, "c", "t", cfgEmpty));
        // wrong topic
        ProducerConfigDTO cfgWrongTopic = buildConfig("t1", "c1", null);
        assertFalse(invokeHasConsumerAccessToTopic(cut, "c1", "t2", cfgWrongTopic));
        // wrong consumer
        ProducerConfigDTO cfgWrongConsumer = buildConfig("t1", "other", null);
        assertFalse(invokeHasConsumerAccessToTopic(cut, "c1", "t1", cfgWrongConsumer));
    }

    // -------------------- Tests for getKafkaConsumer (negative path) --------------------

    @Test
    void test_getKafkaConsumer_throwsInvalidTopic_whenAccessDenied() {
        FederatorService cut = new FederatorService(EMPTY_SHARED_HEADERS);
        TopicRequest req =
                TopicRequest.newBuilder().setTopic("not-allowed").setOffset(0L).build();
        StreamObservable observer = mock(StreamObservable.class);

        ProducerConsumerConfigService mockService = mock(ProducerConsumerConfigService.class);
        ProducerConfigDTO emptyCfg =
                ProducerConfigDTO.builder().producers(new ArrayList<>()).build();

        try (MockedStatic<ProducerConsumerConfigServiceFactory> mockedFactory =
                Mockito.mockStatic(ProducerConsumerConfigServiceFactory.class)) {

            mockedFactory
                    .when(ProducerConsumerConfigServiceFactory::getProducerConsumerConfigService)
                    .thenReturn(mockService);
            when(mockService.getProducerConfiguration()).thenReturn(emptyCfg);

            // Set the gRPC context key so FederatorService can read the consumer id
            Context ctx = Context.current().withValue(GRPCContextKeys.CLIENT_ID, "consumer-1");
            Context previous = ctx.attach();
            try {
                assertThrows(InvalidTopicException.class, () -> cut.getKafkaConsumer(req, observer));
            } finally {
                ctx.detach(previous);
            }
        }
    }
}
