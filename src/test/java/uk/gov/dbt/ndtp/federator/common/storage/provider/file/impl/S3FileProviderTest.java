/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

@ExtendWith(MockitoExtension.class)
class S3FileProviderTest {

    @Mock
    private S3Client s3Client;

    private S3FileProvider s3FileProvider;

    @BeforeEach
    void setUp() {
        s3FileProvider = new S3FileProvider(s3Client);
    }

    @Test
    void testGetSuccess() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, "my-bucket", "my-key");
        HeadObjectResponse headResponse =
                HeadObjectResponse.builder().contentLength(100L).build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headResponse);

        ResponseInputStream<GetObjectResponse> getResponse = mock(ResponseInputStream.class);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(getResponse);

        try (FileTransferResult result = s3FileProvider.get(request)) {
            assertNotNull(result);
            assertEquals(100L, result.fileSize());
            assertEquals(getResponse, result.stream());
        }

        verify(s3Client).headObject(any(HeadObjectRequest.class));
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void testGetS3Exception404() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, "my-bucket", "my-key");
        S3Exception s3Exception = (S3Exception)
                S3Exception.builder().message("Not Found").statusCode(404).build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception);

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> s3FileProvider.get(request));
        assertTrue(exception.getMessage().contains("File not found in S3"));
    }

    @Test
    void testGetS3ExceptionOther() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, "my-bucket", "my-key");
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
                .message("Internal Server Error")
                .statusCode(500)
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception);

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> s3FileProvider.get(request));
        assertTrue(exception.getMessage().contains("S3 error fetching"));
    }

    @Test
    void testGetGeneralException() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, "my-bucket", "my-key");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(new RuntimeException("Generic error"));

        FileFetcherException exception = assertThrows(FileFetcherException.class, () -> s3FileProvider.get(request));
        assertTrue(exception.getMessage().contains("Failed to fetch from S3"));
    }

    @Test
    void testValidatePathSuccess() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, "my-bucket", "my-key");
        HeadObjectResponse headResponse =
                HeadObjectResponse.builder().contentLength(100L).build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headResponse);

        assertDoesNotThrow(() -> s3FileProvider.validatePath(request));
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void testValidatePathObjectNotFound() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, "my-bucket", "my-key");
        S3Exception s3Exception = (S3Exception)
                S3Exception.builder().message("Not Found").statusCode(404).build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception);

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> s3FileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("S3 object not found"));
    }

    @Test
    void testValidatePathMissingBucket() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, null, "my-key");

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> s3FileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("S3 bucket (storageContainer) is required"));
    }

    @Test
    void testValidatePathBlankBucket() {
        FileTransferRequest request = new FileTransferRequest(SourceType.S3, "   ", "my-key");

        FileTransferException exception =
                assertThrows(FileTransferException.class, () -> s3FileProvider.validatePath(request));
        assertTrue(exception.getMessage().contains("S3 bucket (storageContainer) is required"));
    }
}
