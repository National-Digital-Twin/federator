/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.processor.file;

import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

class FileKafkaEventMessageProcessorTest {

    private StreamObservable<FileChunk> mockObserver;
    private FileChunkStreamer mockStreamer;
    private FileKafkaEventMessageProcessor processor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        PropertyUtil.init("client.properties");

        mockObserver = mock(StreamObservable.class);
        mockStreamer = mock(FileChunkStreamer.class);
        processor = new FileKafkaEventMessageProcessor(mockObserver);

        // Inject mockStreamer
        Field field = FileKafkaEventMessageProcessor.class.getDeclaredField("fileChunkStreamer");
        field.setAccessible(true);
        field.set(processor, mockStreamer);
    }

    @AfterEach
    void tearDown() {
        PropertyUtil.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcess() {
        KafkaEvent<String, FileTransferRequest> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, FileTransferRequest> mockRecord = mock(ConsumerRecord.class);
        FileTransferRequest request = mock(FileTransferRequest.class);

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(123L);
        when(mockEvent.value()).thenReturn(request);

        processor.process(mockEvent);

        verify(mockStreamer).stream(123L, request, mockObserver);
    }
}
