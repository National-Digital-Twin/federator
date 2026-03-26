/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

@ExtendWith(MockitoExtension.class)
class GCPFileProviderTest {

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    @Mock
    private ReadChannel readChannel;

    private GCPFileProvider gcpFileProvider;

    @BeforeEach
    void setUp() {
        gcpFileProvider = new GCPFileProvider(storage);
    }

    @Test
    void testGetSuccess() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.exists()).thenReturn(true);
        when(blob.getSize()).thenReturn(100L);
        when(blob.reader()).thenReturn(readChannel);

        try (FileTransferResult result = gcpFileProvider.get(request)) {
            assertNotNull(result);
            assertEquals(100L, result.fileSize());
            assertNotNull(result.stream());
        }

        verify(storage).get(any(BlobId.class));
        verify(blob).exists();
        verify(blob).getSize();
        verify(blob).reader();
    }

    @Test
    void testGetBlobNotFound_NullBlob() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        when(storage.get(any(BlobId.class))).thenReturn(null);

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> gcpFileProvider.get(request));
        assertTrue(exception.getMessage().contains("File not found in GCS"));
    }

    @Test
    void testGetBlobNotFound_BlobDoesNotExist() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.exists()).thenReturn(false);

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> gcpFileProvider.get(request));
        assertTrue(exception.getMessage().contains("File not found in GCS"));
    }

    @Test
    void testGetStorageException404() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        StorageException storageException = new StorageException(404, "Not Found");
        when(storage.get(any(BlobId.class))).thenThrow(storageException);

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> gcpFileProvider.get(request));
        assertTrue(exception.getMessage().contains("File not found in GCS"));
    }

    @Test
    void testGetStorageExceptionOther() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        StorageException storageException = new StorageException(500, "Internal Server Error");
        when(storage.get(any(BlobId.class))).thenThrow(storageException);

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> gcpFileProvider.get(request));
        assertTrue(exception.getMessage().contains("GCS error fetching"));
    }

    @Test
    void testGetGeneralException() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        when(storage.get(any(BlobId.class))).thenThrow(new RuntimeException("Generic error"));

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> gcpFileProvider.get(request));
        assertTrue(exception.getMessage().contains("Failed to fetch from GCS"));
    }

    @Test
    void testValidatePathSuccess() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.exists()).thenReturn(true);

        assertDoesNotThrow(() -> gcpFileProvider.validatePath(request));
        verify(storage).get(any(BlobId.class));
        verify(blob).exists();
    }

    @Test
    void testValidatePathObjectNotFound_NullBlob() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        when(storage.get(any(BlobId.class))).thenReturn(null);

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> gcpFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("GCS object not found"));
    }

    @Test
    void testValidatePathObjectNotFound_BlobDoesNotExist() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.exists()).thenReturn(false);

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> gcpFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("GCS object not found"));
    }

    @Test
    void testValidatePathStorageException404() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        StorageException storageException = new StorageException(404, "Not Found");
        when(storage.get(any(BlobId.class))).thenThrow(storageException);

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> gcpFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("GCS object not found"));
    }

    @Test
    void testValidatePathStorageExceptionOther() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "my-bucket", "my-key");
        StorageException storageException = new StorageException(500, "Internal Server Error");
        when(storage.get(any(BlobId.class))).thenThrow(storageException);

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> gcpFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("GCS validation error"));
    }

    @Test
    void testValidatePathMissingBucket() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, null, "my-key");

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> gcpFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("GCS bucket (storageContainer) is required"));
    }

    @Test
    void testValidatePathBlankBucket() {
        FileTransferRequest request = new FileTransferRequest(SourceType.GCP, "   ", "my-key");

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> gcpFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("GCS bucket (storageContainer) is required"));
    }
}
