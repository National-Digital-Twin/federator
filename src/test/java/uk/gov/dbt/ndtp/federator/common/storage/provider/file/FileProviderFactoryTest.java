/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.azure.storage.blob.BlobServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.s3.S3Client;
import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.AzureBlobClientFactory;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.S3ClientFactory;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl.AzureFileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl.LocalFileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl.S3FileProvider;

class FileProviderFactoryTest {

    private MockedStatic<S3ClientFactory> s3FactoryMock;
    private MockedStatic<AzureBlobClientFactory> azureFactoryMock;

    @BeforeEach
    void setUp() {
        s3FactoryMock = mockStatic(S3ClientFactory.class);
        azureFactoryMock = mockStatic(AzureBlobClientFactory.class);
    }

    @AfterEach
    void tearDown() {
        s3FactoryMock.close();
        azureFactoryMock.close();
    }

    @Test
    void testGetProvider_S3() {
        s3FactoryMock.when(S3ClientFactory::getClient).thenReturn(mock(S3Client.class));
        FileProvider provider = FileProviderFactory.getProvider(SourceType.S3);
        assertTrue(provider instanceof S3FileProvider);
    }

    @Test
    void testGetProvider_Azure() {
        azureFactoryMock.when(AzureBlobClientFactory::getClient).thenReturn(mock(BlobServiceClient.class));
        FileProvider provider = FileProviderFactory.getProvider(SourceType.AZURE);
        assertTrue(provider instanceof AzureFileProvider);
    }

    @Test
    void testGetProvider_Local() {
        FileProvider provider = FileProviderFactory.getProvider(SourceType.LOCAL);
        assertTrue(provider instanceof LocalFileProvider);
    }
}
