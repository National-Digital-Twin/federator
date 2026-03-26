// SPDX-License-Identifier: Apache-2.0
package uk.gov.dbt.ndtp.federator.common.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.Context;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
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
        assertTrue(
                cut.getFilterAttributesForConsumer("client-a", "topic-2", cfg1).isEmpty());
        // config but different consumer
        ProducerConfigDTO cfg2 = buildConfig("topic-1", "client-b", List.of(new AttributesDTO("k", "v", null)));
        assertTrue(
                cut.getFilterAttributesForConsumer("client-a", "topic-1", cfg2).isEmpty());
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
        ExecutorService executorService = mock(ExecutorService.class);

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
                assertThrows(InvalidTopicException.class, () -> cut.streamToClient(req, observer, executorService));
            } finally {
                ctx.detach(previous);
            }
        }
    }

    // -------------------- Positive test for streamToClient --------------------

    @Test
    void test_streamToClient_awaitsTheFutureSubmittedToTheExecutorService() throws IOException {
        KafkaStreamService cut = new KafkaStreamService(EMPTY_SHARED_HEADERS);
        TopicRequest req =
                TopicRequest.newBuilder().setTopic("test").setOffset(0L).build();
        StreamObservable observer = mock(StreamObservable.class);
        ExecutorService executorService = mock(ExecutorService.class);

        ProductDTO mockProductDto = mock(ProductDTO.class);
        ConsumerDTO mockConsumerDto = mock(ConsumerDTO.class);
        ArrayList<ConsumerDTO> mockConumerDtos = new ArrayList<>();
        mockConumerDtos.add(mockConsumerDto);

        when(mockProductDto.getTopic()).thenReturn("test");
        when(mockConsumerDto.getIdpClientId()).thenReturn("consumer-1");
        when(mockProductDto.getConsumers()).thenReturn(mockConumerDtos);

        ArrayList<ProductDTO> mockProductDtos = new ArrayList<>();
        mockProductDtos.add(mockProductDto);

        ProducerDTO mockProducerDto = mock(ProducerDTO.class);
        ArrayList<ProducerDTO> mockProducerDtos = new ArrayList<>();
        mockProducerDtos.add(mockProducerDto);

        when(mockProducerDto.getProducts()).thenReturn(mockProductDtos);

        ProducerConfigService mockService = mock(ProducerConfigService.class);
        ProducerConfigDTO producerCfg =
                ProducerConfigDTO.builder().producers(mockProducerDtos).build();

        try (MockedStatic<ProducerConsumerConfigServiceFactory> mockedFactory =
                Mockito.mockStatic(ProducerConsumerConfigServiceFactory.class)) {
            mockedFactory
                    .when(ProducerConsumerConfigServiceFactory::getProducerConfigService)
                    .thenReturn(mockService);
            when(mockService.getProducerConfiguration()).thenReturn(producerCfg);

            Future mockFuture = mock(Future.class);

            when(executorService.submit(any(Runnable.class))).thenReturn(mockFuture);

            // Set the gRPC context key so KafkaStreamService can read the consumer id
            Context ctx = Context.current().withValue(GRPCContextKeys.CLIENT_ID, "consumer-1");
            Context previous = ctx.attach();

            // Prepare a temporary properties file with minimal configuration
            Path tmp = Files.createTempFile("s3clientfactory-test-", ".properties");
            try {
                String props = String.join(
                        "\n",
                        "kafka.defaultKeyDeserializerClass=org.apache.kafka.common.serialization.StringDeserializer",
                        "kafka.defaultValueDeserializerClass=uk.gov.dbt.ndtp.federator.access.AccessMessageDeserializer",
                        "kafka.bootstrapServers=localhost:9092",
                        "kafka.consumerGroup=test",
                        "kafka.pollRecords=100");

                Files.writeString(tmp, props);
                // Initialize PropertyUtil
                PropertyUtil.init(tmp.toFile());
                cut.streamToClient(req, observer, executorService);
            } finally {
                Files.deleteIfExists(tmp);
                PropertyUtil.clear();
                ctx.detach(previous);
            }

            verify(executorService, times(1)).submit(any(Runnable.class));

            try {
                verify(mockFuture, times(1)).get();
            } catch (InterruptedException | ExecutionException ignored) {
                // ignored
            }
        }
    }
}
