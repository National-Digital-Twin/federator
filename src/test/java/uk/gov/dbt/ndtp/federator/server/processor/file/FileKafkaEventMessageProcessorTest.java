/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.processor.file;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.common.utils.ObjectMapperUtil;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileStreamEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

class FileKafkaEventMessageProcessorTest {

    private StreamObservable<FileStreamEvent> mockObserver;
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
    void testProcessSuccessfully() throws Exception {
        // Given
        KafkaEvent<String, byte[]> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, byte[]> mockRecord = mock(ConsumerRecord.class);

        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "/path/to/file.txt");
        byte[] payload = ObjectMapperUtil.getInstance().writeValueAsBytes(request);

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(123L);
        when(mockEvent.value()).thenReturn(payload);

        // When
        processor.process(mockEvent);

        // Then
        verify(mockStreamer).stream(
                eq(123L),
                argThat(req -> req.path().equals("/path/to/file.txt")
                        && req.sourceType().equals(SourceType.LOCAL)),
                eq(mockObserver));
        verify(mockObserver, never()).onNext(any(FileStreamEvent.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithDeserializationError() {
        // Given
        KafkaEvent<String, byte[]> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, byte[]> mockRecord = mock(ConsumerRecord.class);

        byte[] invalidPayload = "invalid json".getBytes();

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(456L);
        when(mockEvent.value()).thenReturn(invalidPayload);

        // When
        processor.process(mockEvent);

        // Then
        verify(mockStreamer, never()).stream(anyLong(), any(), any());
        verify(mockObserver)
                .onNext(argThat(event -> event.hasWarning()
                        && event.getWarning().getSkippedSequenceId() == 456L
                        && event.getWarning().getReason().equals("DESERIALIZATION")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithNullRequest() throws Exception {
        // Given
        KafkaEvent<String, byte[]> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, byte[]> mockRecord = mock(ConsumerRecord.class);

        byte[] payload = ObjectMapperUtil.getInstance().writeValueAsBytes(null);

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(789L);
        when(mockEvent.value()).thenReturn(payload);

        // When
        processor.process(mockEvent);

        // Then
        verify(mockStreamer, never()).stream(anyLong(), any(), any());
        verify(mockObserver)
                .onNext(argThat(event -> event.hasWarning()
                        && event.getWarning().getSkippedSequenceId() == 789L
                        && event.getWarning().getReason().equals("VALIDATION")
                        && event.getWarning().getDetails().contains("FileTransferRequest is null")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithBlankPath() throws Exception {
        // Given
        KafkaEvent<String, byte[]> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, byte[]> mockRecord = mock(ConsumerRecord.class);

        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "");
        byte[] payload = ObjectMapperUtil.getInstance().writeValueAsBytes(request);

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(111L);
        when(mockEvent.value()).thenReturn(payload);

        // When
        processor.process(mockEvent);

        // Then
        verify(mockStreamer, never()).stream(anyLong(), any(), any());
        verify(mockObserver)
                .onNext(argThat(event -> event.hasWarning()
                        && event.getWarning().getSkippedSequenceId() == 111L
                        && event.getWarning().getReason().equals("VALIDATION")
                        && event.getWarning().getDetails().contains("FileTransferRequest.path is blank")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithMalformedJsonMissingPath() {
        // Given - JSON with null path will fail deserialization due to record validation
        KafkaEvent<String, byte[]> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, byte[]> mockRecord = mock(ConsumerRecord.class);

        // Malformed JSON that will cause deserialization error
        String malformedJson = "{\"sourceType\":\"LOCAL\",\"storageContainer\":null,\"path\":null}";
        byte[] payload = malformedJson.getBytes();

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(222L);
        when(mockEvent.value()).thenReturn(payload);

        // When
        processor.process(mockEvent);

        // Then
        verify(mockStreamer, never()).stream(anyLong(), any(), any());
        verify(mockObserver)
                .onNext(argThat(event -> event.hasWarning()
                        && event.getWarning().getSkippedSequenceId() == 222L
                        && (event.getWarning().getReason().equals("DESERIALIZATION")
                                || event.getWarning().getReason().equals("VALIDATION"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithWhitespacePath() throws Exception {
        // Given
        KafkaEvent<String, byte[]> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, byte[]> mockRecord = mock(ConsumerRecord.class);

        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "   ");
        byte[] payload = ObjectMapperUtil.getInstance().writeValueAsBytes(request);

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(333L);
        when(mockEvent.value()).thenReturn(payload);

        // When
        processor.process(mockEvent);

        // Then
        verify(mockStreamer, never()).stream(anyLong(), any(), any());
        verify(mockObserver)
                .onNext(argThat(event -> event.hasWarning()
                        && event.getWarning().getSkippedSequenceId() == 333L
                        && event.getWarning().getReason().equals("VALIDATION")
                        && event.getWarning().getDetails().contains("FileTransferRequest.path is blank")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithUnexpectedException() throws Exception {
        // Given
        KafkaEvent<String, byte[]> mockEvent = mock(KafkaEvent.class);
        ConsumerRecord<String, byte[]> mockRecord = mock(ConsumerRecord.class);

        FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "/path/to/file.txt");
        byte[] payload = ObjectMapperUtil.getInstance().writeValueAsBytes(request);

        when(mockEvent.getConsumerRecord()).thenReturn(mockRecord);
        when(mockRecord.offset()).thenReturn(999L);
        when(mockEvent.value()).thenReturn(payload);

        // Simulate unexpected exception during streaming
        doThrow(new RuntimeException("Unexpected error")).when(mockStreamer).stream(anyLong(), any(), any());

        // When
        processor.process(mockEvent);

        // Then
        verify(mockObserver)
                .onNext(argThat(event -> event.hasWarning()
                        && event.getWarning().getSkippedSequenceId() == 999L
                        && event.getWarning().getReason().equals("VALIDATION")
                        && event.getWarning().getDetails().contains("Unexpected error")));
    }
}
