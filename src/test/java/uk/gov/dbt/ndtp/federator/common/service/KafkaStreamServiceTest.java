// SPDX-License-Identifier: Apache-2.0
package uk.gov.dbt.ndtp.federator.common.service;

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
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.common.service.config.ProducerConfigService;
import uk.gov.dbt.ndtp.federator.common.service.kafka.KafkaStreamService;
import uk.gov.dbt.ndtp.federator.common.utils.ProducerConsumerConfigServiceFactory;
import uk.gov.dbt.ndtp.federator.server.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

class KafkaStreamServiceTest {

    private static final Set<String> EMPTY_SHARED_HEADERS = Set.of();

    // -------------------- Helper reflection methods --------------------

    private boolean invokeHasConsumerAccessToTopic(
            KafkaStreamService cut, String consumerId, String topic, ProducerConfigDTO cfg) {
        try {
            Method m = KafkaStreamService.class.getDeclaredMethod(
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
        KafkaStreamService cut = new KafkaStreamService(EMPTY_SHARED_HEADERS);
        List<AttributesDTO> attrs = List.of(new AttributesDTO("tenant", "alpha", "String"));
        ProducerConfigDTO cfg = buildConfig("telemetry.raw", "client-a", attrs);

        List<AttributesDTO> result = cut.getFilterAttributesForConsumer("client-a", "telemetry.raw", cfg);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("tenant", result.get(0).getName());
        assertEquals("alpha", result.get(0).getValue());
    }

    @Test
    void test_getFilterAttributesForConsumer_returnsEmpty_whenNoMatchOrNulls() {
        KafkaStreamService cut = new KafkaStreamService(EMPTY_SHARED_HEADERS);
        // null config
        assertEquals(Collections.emptyList(), cut.getFilterAttributesForConsumer("x", "y", null));
        // config but different topic
        ProducerConfigDTO cfg1 = buildConfig("topic-1", "client-a", List.of(new AttributesDTO("k", "v", null)));
        assertTrue(cut.getFilterAttributesForConsumer("client-a", "topic-2", cfg1)
                .isEmpty());
        // config but different consumer
        ProducerConfigDTO cfg2 = buildConfig("topic-1", "client-b", List.of(new AttributesDTO("k", "v", null)));
        assertTrue(cut.getFilterAttributesForConsumer("client-a", "topic-1", cfg2)
                .isEmpty());
    }

    // -------------------- Tests for hasConsumerAccessToTopic --------------------

    @Test
    void test_hasConsumerAccessToTopic_trueWhenMatching() {
        KafkaStreamService cut = new KafkaStreamService(EMPTY_SHARED_HEADERS);
        ProducerConfigDTO cfg = buildConfig("dp1", "CLIENT-123", null);
        assertTrue(invokeHasConsumerAccessToTopic(cut, "client-123", "dp1", cfg));
    }

    @Test
    void test_hasConsumerAccessToTopic_falseWhenNoProducersOrNoMatch() {
        KafkaStreamService cut = new KafkaStreamService(EMPTY_SHARED_HEADERS);
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

    // -------------------- Negative test for streamToClient (access denied) --------------------

    @Test
    void test_streamToClient_throwsInvalidTopic_whenAccessDenied() {
        KafkaStreamService cut = new KafkaStreamService(EMPTY_SHARED_HEADERS);
        TopicRequest req =
                TopicRequest.newBuilder().setTopic("not-allowed").setOffset(0L).build();
        StreamObservable observer = mock(StreamObservable.class);

        ProducerConfigService mockService = mock(ProducerConfigService.class);
        ProducerConfigDTO emptyCfg =
                ProducerConfigDTO.builder().producers(new ArrayList<>()).build();

        try (MockedStatic<ProducerConsumerConfigServiceFactory> mockedFactory =
                Mockito.mockStatic(ProducerConsumerConfigServiceFactory.class)) {
            mockedFactory
                    .when(ProducerConsumerConfigServiceFactory::getProducerConfigService)
                    .thenReturn(mockService);
            when(mockService.getProducerConfiguration()).thenReturn(emptyCfg);

            // Set the gRPC context key so KafkaStreamService can read the consumer id
            Context ctx = Context.current().withValue(GRPCContextKeys.CLIENT_ID, "consumer-1");
            Context previous = ctx.attach();
            try {
                assertThrows(InvalidTopicException.class, () -> cut.streamToClient(req, observer));
            } finally {
                ctx.detach(previous);
            }
        }
    }
}
