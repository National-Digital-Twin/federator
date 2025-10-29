// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.

package uk.gov.dbt.ndtp.federator.server.grpc.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.common.service.config.ProducerConfigService;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.utils.ProducerConsumerConfigServiceFactory;

class ConsumerVerificationServerInterceptorTest {

    private IdpTokenService idpTokenService;
    private ConsumerVerificationServerInterceptor cut;

    @BeforeEach
    void setUp() {
        idpTokenService = mock(IdpTokenService.class);
        // Provide properties with required audience client id
        Properties props = new Properties();
        props.setProperty("idp.client.id", "svc-client");
        cut = new ConsumerVerificationServerInterceptor(idpTokenService, props);
        // Default audience extraction to include required client id so calls can proceed to authz logic
        when(idpTokenService.extractAudiencesFromToken(any())).thenReturn(List.of("svc-client"));
    }

    @AfterEach
    void tearDown() {
        // ensure no leaked context
        while (Context.current() != Context.ROOT) {
            try {
                Context.current().detach(Context.ROOT.attach());
            } catch (Throwable ignored) {
                break;
            }
        }
    }

    // ------------ Helper builders ------------

    private ProducerConfigDTO cfgWithFirstProducer(ProducerDTO firstProducer) {
        List<ProducerDTO> list = new ArrayList<>();
        list.add(firstProducer);
        return ProducerConfigDTO.builder().producers(list).build();
    }

    private ProducerDTO producerWithProducts(ProductDTO... products) {
        ProducerDTO p = ProducerDTO.builder().products(new ArrayList<>()).build();
        if (products != null) {
            for (ProductDTO prod : products) {
                p.getProducts().add(prod);
            }
        }
        return p;
    }

    private ProductDTO productWithConsumers(String topic, ConsumerDTO... consumers) {
        ProductDTO pd =
                ProductDTO.builder().topic(topic).consumers(new ArrayList<>()).build();
        if (consumers != null) {
            for (ConsumerDTO c : consumers) {
                pd.getConsumers().add(c);
            }
        }
        return pd;
    }

    private ConsumerDTO consumer(String idpClientId) {
        return ConsumerDTO.builder().idpClientId(idpClientId).build();
    }

    // ------------ Tests for isConsumerAuthorized (via reflection to access private) ------------

    private boolean invokeIsConsumerAuthorized(ProducerConfigDTO cfg, String consumerId) {
        try {
            var m = ConsumerVerificationServerInterceptor.class.getDeclaredMethod(
                    "isConsumerAuthorized", ProducerConfigDTO.class, String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(cut, cfg, consumerId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void isConsumerAuthorized_false_whenConfigNullOrNoProducers() {
        assertFalse(invokeIsConsumerAuthorized(null, "c1"));
        ProducerConfigDTO cfg = ProducerConfigDTO.builder().producers(null).build();
        assertFalse(invokeIsConsumerAuthorized(cfg, "c1"));
    }

    @Test
    void isConsumerAuthorized_false_whenFirstProducerHasNoProducts() {
        // products null in the first producer
        ProducerDTO first = ProducerDTO.builder().products(null).build();
        ProducerConfigDTO cfg = cfgWithFirstProducer(first);
        assertFalse(invokeIsConsumerAuthorized(cfg, "c1"));
    }

    @Test
    void isConsumerAuthorized_false_whenNoMatchingConsumerAcrossProducts() {
        ProducerDTO first = producerWithProducts(
                productWithConsumers("topic-1", consumer("alice")), productWithConsumers("topic-2", consumer("bob")));
        ProducerConfigDTO cfg = cfgWithFirstProducer(first);
        assertFalse(invokeIsConsumerAuthorized(cfg, "charlie"));
    }

    @Test
    void isConsumerAuthorized_true_whenAnyConsumerMatches_caseInsensitive() {
        ProducerDTO first = producerWithProducts(
                productWithConsumers("topic-1", consumer("ALICE")), productWithConsumers("topic-2", consumer("bob")));
        ProducerConfigDTO cfg = cfgWithFirstProducer(first);
        assertTrue(invokeIsConsumerAuthorized(cfg, "alice"));
    }

    // ------------ Tests for interceptCall flows ------------

    @Test
    void interceptCall_closesUnauthenticated_whenBlankConsumerId() {
        // given
        when(idpTokenService.extractClientIdFromToken(any())).thenReturn("");
        Metadata headers = new Metadata();
        Metadata.Key<String> authorization = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(authorization, "Bearer token");

        @SuppressWarnings("unchecked")
        ServerCall<String, String> call = mock(ServerCall.class);
        MethodDescriptor<String, String> md = MethodDescriptor.<String, String>newBuilder()
                .setFullMethodName("svc/method")
                .setType(MethodDescriptor.MethodType.UNARY)
                .setRequestMarshaller(new MethodDescriptor.Marshaller<>() {
                    public InputStream stream(String value) {
                        return new java.io.ByteArrayInputStream(new byte[0]);
                    }

                    public String parse(InputStream stream) {
                        return "";
                    }
                })
                .setResponseMarshaller(new MethodDescriptor.Marshaller<>() {
                    public InputStream stream(String value) {
                        return new java.io.ByteArrayInputStream(new byte[0]);
                    }

                    public String parse(InputStream stream) {
                        return "";
                    }
                })
                .build();
        when(call.getMethodDescriptor()).thenReturn(md);

        @SuppressWarnings("unchecked")
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);

        // when
        ServerCall.Listener<String> listener = cut.interceptCall(call, headers, next);

        // then
        assertNotNull(listener);
        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any());
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void interceptCall_closesPermissionDenied_whenNotAuthorized() {
        // given
        when(idpTokenService.extractClientIdFromToken(any())).thenReturn("consumer-1");

        ProducerConfigService mockSvc = mock(ProducerConfigService.class);
        // Build configuration where first producer has no products (thus unauthorized)
        ProducerDTO first = ProducerDTO.builder().products(null).build();
        ProducerConfigDTO cfg =
                ProducerConfigDTO.builder().producers(List.of(first)).build();

        try (MockedStatic<ProducerConsumerConfigServiceFactory> mocked =
                Mockito.mockStatic(ProducerConsumerConfigServiceFactory.class)) {
            mocked.when(ProducerConsumerConfigServiceFactory::getProducerConfigService)
                    .thenReturn(mockSvc);
            when(mockSvc.getProducerConfiguration()).thenReturn(cfg);

            Metadata headers = new Metadata();
            Metadata.Key<String> authorization = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
            headers.put(authorization, "Bearer token");

            @SuppressWarnings("unchecked")
            ServerCall<String, String> call = mock(ServerCall.class);
            MethodDescriptor<String, String> md = MethodDescriptor.<String, String>newBuilder()
                    .setFullMethodName("svc/method")
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setRequestMarshaller(new MethodDescriptor.Marshaller<>() {
                        public InputStream stream(String value) {
                            return new java.io.ByteArrayInputStream(new byte[0]);
                        }

                        public String parse(InputStream stream) {
                            return "";
                        }
                    })
                    .setResponseMarshaller(new MethodDescriptor.Marshaller<>() {
                        public InputStream stream(String value) {
                            return new java.io.ByteArrayInputStream(new byte[0]);
                        }

                        public String parse(InputStream stream) {
                            return "";
                        }
                    })
                    .build();
            when(call.getMethodDescriptor()).thenReturn(md);

            @SuppressWarnings("unchecked")
            ServerCallHandler<String, String> next = mock(ServerCallHandler.class);

            // when
            ServerCall.Listener<String> listener = cut.interceptCall(call, headers, next);

            // then
            assertNotNull(listener);
            verify(call).close(argThat(status -> status.getCode() == Status.Code.PERMISSION_DENIED), any());
            verify(next, never()).startCall(any(), any());
        }
    }

    @Test
    void interceptCall_proceeds_whenAuthorized() {
        // given
        when(idpTokenService.extractClientIdFromToken(any())).thenReturn("CLIENT-A");

        // Build configuration where first producer has a product with a matching consumer idpClientId
        ConsumerDTO cons = ConsumerDTO.builder().idpClientId("client-a").build();
        ProductDTO prod =
                ProductDTO.builder().topic("t1").consumers(new ArrayList<>()).build();
        prod.getConsumers().add(cons);
        ProducerDTO first = ProducerDTO.builder().products(new ArrayList<>()).build();
        first.getProducts().add(prod);
        ProducerConfigDTO cfg =
                ProducerConfigDTO.builder().producers(List.of(first)).build();

        ProducerConfigService mockSvc = mock(ProducerConfigService.class);
        try (MockedStatic<ProducerConsumerConfigServiceFactory> mocked =
                Mockito.mockStatic(ProducerConsumerConfigServiceFactory.class)) {
            mocked.when(ProducerConsumerConfigServiceFactory::getProducerConfigService)
                    .thenReturn(mockSvc);
            when(mockSvc.getProducerConfiguration()).thenReturn(cfg);

            Metadata headers = new Metadata();
            Metadata.Key<String> authorization = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
            headers.put(authorization, "Bearer token");

            @SuppressWarnings("unchecked")
            ServerCall<String, String> call = mock(ServerCall.class);
            MethodDescriptor<String, String> md = MethodDescriptor.<String, String>newBuilder()
                    .setFullMethodName("svc/method")
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setRequestMarshaller(new MethodDescriptor.Marshaller<>() {
                        public InputStream stream(String value) {
                            return new java.io.ByteArrayInputStream(new byte[0]);
                        }

                        public String parse(InputStream stream) {
                            return "";
                        }
                    })
                    .setResponseMarshaller(new MethodDescriptor.Marshaller<>() {
                        public InputStream stream(String value) {
                            return new java.io.ByteArrayInputStream(new byte[0]);
                        }

                        public String parse(InputStream stream) {
                            return "";
                        }
                    })
                    .build();
            when(call.getMethodDescriptor()).thenReturn(md);

            @SuppressWarnings("unchecked")
            ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
            @SuppressWarnings("unchecked")
            ServerCall.Listener<String> expectedListener = mock(ServerCall.Listener.class);
            when(next.startCall(any(), any())).thenReturn(expectedListener);

            // when
            ServerCall.Listener<String> actual = cut.interceptCall(call, headers, next);

            // then
            assertNotNull(actual);
            // verify next was called (meaning we proceeded)
            verify(next, times(1)).startCall(any(), any());
            // and call was not closed with an error
            verify(call, never()).close(any(), any());
        }
    }
}
