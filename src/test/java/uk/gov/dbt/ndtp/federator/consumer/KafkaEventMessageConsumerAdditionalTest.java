// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil.setUpProperties;

import java.time.Duration;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;

/**
 * Additional focused tests for KafkaEventMessageConsumer to cover inactivity timeout and close delegation.
 */
class KafkaEventMessageConsumerAdditionalTest {

    private static final KafkaEventSource.Builder mockKafkaBuilder = mock(KafkaEventSource.Builder.class);
    private static final KafkaEventSource mockEventSource = mock(KafkaEventSource.class);
    private static final MockedStatic<KafkaUtil> mockedKafkaUtil = Mockito.mockStatic(KafkaUtil.class);

    @BeforeAll
    static void beforeAll() {
        setUpProperties();
        mockedKafkaUtil.when(KafkaUtil::getKafkaSourceBuilder).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.keyDeserializer(StringDeserializer.class)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.valueDeserializer(StringDeserializer.class)).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.topic("TOPIC")).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.consumerGroup("CLIENT"))
                .thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.readPolicy(any())).thenReturn(mockKafkaBuilder);
        when(mockKafkaBuilder.build()).thenReturn(mockEventSource);
        // Default: source isn't closed unless we explicitly close it
        when(mockEventSource.isClosed()).thenReturn(false);
        // Allow close() without side-effects
        doNothing().when(mockEventSource).close();
    }

    @AfterAll
    static void afterAll() {
        mockedKafkaUtil.close();
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
