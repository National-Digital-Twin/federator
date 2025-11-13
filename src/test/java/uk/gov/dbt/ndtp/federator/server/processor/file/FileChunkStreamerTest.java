// SPDX-License-Identifier: Apache-2.0
package uk.gov.dbt.ndtp.federator.server.processor.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils.*;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProviderFactory;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileChunk;

class FileChunkStreamerTest {

    // Helper to avoid importing Arrays copyOfRange (keep Java 8 compatible)
    private static byte[] copyOfRange(byte[] src, int from, int to) {
        int len = Math.max(0, to - from);
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = src[from + i];
        }
        return out;
    }

    @Test
    void test_stream_success_sendsDataChunksAndLastChunkChecksum() throws Exception {
        // Arrange
        byte[] data = "Hello World! This is a test for chunking.".getBytes(StandardCharsets.UTF_8);
        int chunkSize = 8; // force multiple chunks
        long fileSize = data.length;
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        FileProvider mockFetcher = mock(FileProvider.class);
        when(mockFetcher.get(any())).thenReturn(new FileTransferResult(bais, fileSize));

        try (MockedStatic<FileProviderFactory> mockedFactory = Mockito.mockStatic(FileProviderFactory.class)) {
            mockedFactory.when(() -> FileProviderFactory.getProvider(any())).thenReturn(mockFetcher);

            FileChunkStreamer cut = new FileChunkStreamer(chunkSize);
            CapturingObserver observer = new CapturingObserver();
            FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "test-file.txt");

            // Act
            cut.stream(42L, request, observer);

            // Assert
            assertNull(observer.error, "Should not error");
            assertFalse(observer.completed, "FileChunkStreamer does not call onCompleted, processor does");
            assertFalse(observer.chunks.isEmpty());

            // Last element is last-chunk marker
            FileChunk last = observer.chunks.get(observer.chunks.size() - 1);
            assertTrue(last.getIsLastChunk());
            assertEquals(fileSize, last.getFileSize());
            assertEquals("test-file.txt", last.getFileName());

            // Compute expected checksum
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data);
            String expectedChecksum = bytesToHex(digest.digest());
            assertEquals(expectedChecksum, last.getFileChecksum());

            // Check data chunks consistency
            int totalChunks = (int) (fileSize / chunkSize + (fileSize % chunkSize == 0 ? 0 : 1));
            // last index is number of data chunks
            assertEquals(totalChunks, last.getChunkIndex());

            int dataChunks = observer.chunks.size() - 1; // exclude last marker
            assertEquals(totalChunks, dataChunks);
            for (int i = 0; i < dataChunks; i++) {
                FileChunk c = observer.chunks.get(i);
                assertFalse(c.getIsLastChunk());
                assertEquals(i, c.getChunkIndex());
                assertEquals(totalChunks, c.getTotalChunks());
                assertEquals(fileSize, c.getFileSize());
                assertEquals("test-file.txt", c.getFileName());
                assertEquals(42L, c.getFileSequenceId());
            }

            // Verify first chunk bytes
            ByteString firstBytes = observer.chunks.get(0).getChunkData();
            assertArrayEquals(copyOfRange(data, 0, Math.min(chunkSize, data.length)), firstBytes.toByteArray());
        }
    }

    @Test
    void test_stream_error_propagatesAsInternalStatus() {
        FileProvider mockFetcher = mock(FileProvider.class);
        when(mockFetcher.get(any())).thenThrow(new RuntimeException("boom"));

        try (MockedStatic<FileProviderFactory> mockedFactory = Mockito.mockStatic(FileProviderFactory.class)) {
            mockedFactory.when(() -> FileProviderFactory.getProvider(any())).thenReturn(mockFetcher);

            FileChunkStreamer cut = new FileChunkStreamer(4);
            CapturingObserver observer = new CapturingObserver();
            FileTransferRequest request = new FileTransferRequest(SourceType.LOCAL, null, "bad.txt");

            cut.stream(7L, request, observer);

            assertNotNull(observer.error, "Error should be propagated");
            assertTrue(observer.error instanceof StatusRuntimeException);
            StatusRuntimeException sre = (StatusRuntimeException) observer.error;
            assertEquals(io.grpc.Status.Code.INTERNAL, sre.getStatus().getCode());
        }
    }

    private static class CapturingObserver implements StreamObservable<FileChunk> {
        final List<FileChunk> chunks = new ArrayList<>();
        Exception error;
        boolean completed;
        Runnable cancelHandler;
        boolean cancelled;

        @Override
        public void onNext(FileChunk value) {
            chunks.add(value);
        }

        @Override
        public void setOnCancelHandler(Runnable onCancelHandler) {
            this.cancelHandler = onCancelHandler;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void onError(Exception e) {
            this.error = e;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
