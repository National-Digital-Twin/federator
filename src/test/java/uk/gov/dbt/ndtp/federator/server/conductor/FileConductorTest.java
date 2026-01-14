/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.conductor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.server.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

class FileConductorTest {

    private StreamObservable<FileChunk> mockObserver;
    private MessageConsumer<KafkaEvent<String, FileTransferRequest>> mockConsumer;
    private MessageProcessor<KafkaEvent<String, FileTransferRequest>> mockProcessor;
    private List<AttributesDTO> filterAttributes;
    private FileConductor conductor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mockObserver = mock(StreamObservable.class);
        mockConsumer = mock(MessageConsumer.class);
        mockProcessor = mock(MessageProcessor.class);
        filterAttributes = Collections.emptyList();

        // Use reflection to access the private constructor
        Constructor<FileConductor> constructor = FileConductor.class.getDeclaredConstructor(
                StreamObservable.class, MessageConsumer.class, List.class, MessageProcessor.class);
        constructor.setAccessible(true);
        conductor = constructor.newInstance(mockObserver, mockConsumer, filterAttributes, mockProcessor);
    }

    @Test
    void testContinueProcessing_ObserverCancelled() {
        when(mockObserver.isCancelled()).thenReturn(true);

        boolean result = conductor.continueProcessing();

        assertFalse(result);
        verify(mockConsumer).close();
    }

    @Test
    void testContinueProcessing_ObserverNotCancelled_ConsumerAvailable() {
        when(mockObserver.isCancelled()).thenReturn(false);
        when(mockConsumer.stillAvailable()).thenReturn(true);

        assertTrue(conductor.continueProcessing());
    }

    @Test
    void testContinueProcessing_ObserverNotCancelled_ConsumerNotAvailable() {
        when(mockObserver.isCancelled()).thenReturn(false);
        when(mockConsumer.stillAvailable()).thenReturn(false);

        assertFalse(conductor.continueProcessing());
    }
}
