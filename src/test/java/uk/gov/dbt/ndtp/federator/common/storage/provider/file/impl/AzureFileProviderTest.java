/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
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
class AzureFileProviderTest {

    @Mock
    private BlobServiceClient blobServiceClient;

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private BlobClient blobClient;

    private AzureFileProvider azureFileProvider;

    @BeforeEach
    void setUp() {
        azureFileProvider = new AzureFileProvider(blobServiceClient);
    }

    @Test
    void testGetSuccess() {
        FileTransferRequest request = new FileTransferRequest(SourceType.AZURE, "container", "path");
        when(blobServiceClient.getBlobContainerClient("container")).thenReturn(blobContainerClient);
        when(blobContainerClient.getBlobClient("path")).thenReturn(blobClient);

        BlobProperties props = mock(BlobProperties.class);
        when(props.getBlobSize()).thenReturn(200L);
        when(blobClient.getProperties()).thenReturn(props);

        BlobInputStream stream = mock(BlobInputStream.class);
        when(blobClient.openInputStream()).thenReturn(stream);

        try (FileTransferResult result = azureFileProvider.get(request)) {
            assertNotNull(result);
            assertEquals(200L, result.fileSize());
            assertEquals(stream, result.stream());
        }
    }

    @Test
    void testGetFailure() {
        FileTransferRequest request = new FileTransferRequest(SourceType.AZURE, "container", "path");
        when(blobServiceClient.getBlobContainerClient("container")).thenThrow(new RuntimeException("Azure error"));

        assertThrows(FileFetcherException.class, () -> azureFileProvider.get(request));
    }

    @Test
    void testValidatePathSuccess() {
        FileTransferRequest request = new FileTransferRequest(SourceType.AZURE, "container", "path");
        when(blobServiceClient.getBlobContainerClient("container")).thenReturn(blobContainerClient);
        when(blobContainerClient.getBlobClient("path")).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);

        assertDoesNotThrow(() -> azureFileProvider.validatePath(request));
    }

    @Test
    void testValidatePathBlobNotFound() {
        FileTransferRequest request = new FileTransferRequest(SourceType.AZURE, "container", "path");
        when(blobServiceClient.getBlobContainerClient("container")).thenReturn(blobContainerClient);
        when(blobContainerClient.getBlobClient("path")).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> azureFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("Azure blob not found"));
    }

    @Test
    void testValidatePathMissingContainer() {
        FileTransferRequest request = new FileTransferRequest(SourceType.AZURE, null, "path");

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> azureFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("Azure container (storageContainer) is required"));
    }

    @Test
    void testValidatePathBlankContainer() {
        FileTransferRequest request = new FileTransferRequest(SourceType.AZURE, "   ", "path");

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> azureFileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("Azure container (storageContainer) is required"));
    }
}
