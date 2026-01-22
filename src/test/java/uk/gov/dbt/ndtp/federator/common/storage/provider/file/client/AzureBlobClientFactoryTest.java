/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.WorkloadIdentityCredential;
import com.azure.identity.WorkloadIdentityCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.lang.reflect.Field;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ConfigurationException;

class AzureBlobClientFactoryTest {

    @BeforeEach
    void setUp() {
        // Do not load classpath properties here; each test sets up its own PropertyUtil state
        resetSingleton();
    }

    @AfterEach
    void tearDown() {
        PropertyUtil.clear();
        resetSingleton();
    }

    private void resetSingleton() {
        try {
            Field instanceField = AzureBlobClientFactory.class.getDeclaredField("client");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            fail("Failed to reset singleton: " + e.getMessage());
        }
    }

    @Test
    void testGetClient_ThrowsExceptionWhenNoConnectionString() {
        // Ensure PropertyUtil is initialized but without relevant properties
        PropertyUtil.clear();
        // Create a temporary file with empty properties to init PropertyUtil
        try {
            java.io.File temp = java.io.File.createTempFile("empty", ".properties");
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        assertThrows(ConfigurationException.class, AzureBlobClientFactory::getClient);
    }

    @Test
    void testGetClient_Success() {
        String dummyConnString =
                "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net";

        // Initialize PropertyUtil with the dummy connection string
        PropertyUtil.clear();
        try {
            java.io.File temp = java.io.File.createTempFile("test", ".properties");
            Files.writeString(temp.toPath(), "azure.storage.connection.string=" + dummyConnString);
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        try (MockedConstruction<BlobServiceClientBuilder> mockedBuilder =
                mockConstruction(BlobServiceClientBuilder.class, (mock, context) -> {
                    when(mock.connectionString(anyString())).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mock(BlobServiceClient.class));
                })) {

            BlobServiceClient client = AzureBlobClientFactory.getClient();

            assertNotNull(client);
            verify(mockedBuilder.constructed().get(0)).connectionString(dummyConnString);
            verify(mockedBuilder.constructed().get(0)).buildClient();
        }
    }

    @Test
    void testGetClient_Endpoint_Success() {
        String dummyEndpoint = "https://test.blob.core.windows.net";

        PropertyUtil.clear();
        try {
            java.io.File temp = java.io.File.createTempFile("test", ".properties");
            Files.writeString(temp.toPath(), "azure.storage.endpoint=" + dummyEndpoint);
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        try (MockedConstruction<WorkloadIdentityCredentialBuilder> mockedCredBuilder =
                        mockConstruction(WorkloadIdentityCredentialBuilder.class, (mock, context) -> {
                            when(mock.build()).thenReturn(mock(WorkloadIdentityCredential.class));
                        });
                MockedConstruction<BlobServiceClientBuilder> mockedBuilder =
                        mockConstruction(BlobServiceClientBuilder.class, (mock, context) -> {
                            when(mock.credential(any(TokenCredential.class))).thenReturn(mock);
                            when(mock.endpoint(anyString())).thenReturn(mock);
                            when(mock.buildClient()).thenReturn(mock(BlobServiceClient.class));
                        })) {

            BlobServiceClient client = AzureBlobClientFactory.getClient();

            assertNotNull(client);
            verify(mockedBuilder.constructed().get(0)).endpoint(dummyEndpoint);
            verify(mockedBuilder.constructed().get(0)).credential(any(TokenCredential.class));
            verify(mockedBuilder.constructed().get(0)).buildClient();
        }
    }

    @Test
    void testGetClient_Endpoint_Failure() {
        String dummyEndpoint = "https://test.blob.core.windows.net";

        PropertyUtil.clear();
        try {
            java.io.File temp = java.io.File.createTempFile("test", ".properties");
            Files.writeString(temp.toPath(), "azure.storage.endpoint=" + dummyEndpoint);
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        try (MockedConstruction<WorkloadIdentityCredentialBuilder> mockedCredBuilder =
                mockConstruction(WorkloadIdentityCredentialBuilder.class, (mock, context) -> {
                    when(mock.build()).thenThrow(new RuntimeException("Credential error"));
                })) {

            assertThrows(ConfigurationException.class, AzureBlobClientFactory::getClient);
        }
    }

    @Test
    void testGetClient_ConnectionString_Blank_ThrowsException() {
        PropertyUtil.clear();
        try {
            java.io.File temp = java.io.File.createTempFile("test", ".properties");
            Files.writeString(temp.toPath(), "azure.storage.connection.string=  ");
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        assertThrows(ConfigurationException.class, AzureBlobClientFactory::getClient);
    }

    @Test
    void testGetClient_Endpoint_Blank_ThrowsException() {
        PropertyUtil.clear();
        try {
            java.io.File temp = java.io.File.createTempFile("test", ".properties");
            Files.writeString(temp.toPath(), "azure.storage.endpoint=  ");
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        assertThrows(ConfigurationException.class, AzureBlobClientFactory::getClient);
    }

    @Test
    void testGetClient_ReturnsSingleton_InstanceReused() {
        String dummyConnString =
                "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net";

        // Initialize PropertyUtil with the dummy connection string
        PropertyUtil.clear();
        try {
            java.io.File temp = java.io.File.createTempFile("test", ".properties");
            Files.writeString(temp.toPath(), "azure.storage.connection.string=" + dummyConnString);
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        BlobServiceClient mockClient = mock(BlobServiceClient.class);

        try (MockedConstruction<BlobServiceClientBuilder> mockedBuilder =
                mockConstruction(BlobServiceClientBuilder.class, (mock, context) -> {
                    when(mock.connectionString(anyString())).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockClient);
                })) {

            BlobServiceClient client1 = AzureBlobClientFactory.getClient();
            BlobServiceClient client2 = AzureBlobClientFactory.getClient();

            assertSame(client1, client2, "Expected singleton instance to be reused");
            assertSame(mockClient, client1, "Expected returned client to be the mock instance");

            // BlobServiceClientBuilder should be constructed only once
            assertEquals(1, mockedBuilder.constructed().size());
            verify(mockedBuilder.constructed().get(0)).connectionString(dummyConnString);
            // buildClient should be called only once since subsequent calls reuse the singleton
            verify(mockedBuilder.constructed().get(0), times(1)).buildClient();
        }
    }

    @Test
    void testGetClient_ConnectionString_TakesPrecedence_WhenBothSet() {
        String dummyConnString =
                "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net";
        String dummyEndpoint = "https://test.blob.core.windows.net";

        // Initialize PropertyUtil with both properties; connection string should win
        PropertyUtil.clear();
        try {
            java.io.File temp = java.io.File.createTempFile("test", ".properties");
            Files.writeString(
                    temp.toPath(),
                    "azure.storage.connection.string=" + dummyConnString + System.lineSeparator()
                            + "azure.storage.endpoint=" + dummyEndpoint);
            PropertyUtil.init(temp);
            temp.delete();
        } catch (java.io.IOException e) {
            fail("Failed to create temp file");
        }

        try (MockedConstruction<WorkloadIdentityCredentialBuilder> mockedCredBuilder =
                        mockConstruction(WorkloadIdentityCredentialBuilder.class);
                MockedConstruction<BlobServiceClientBuilder> mockedBuilder =
                        mockConstruction(BlobServiceClientBuilder.class, (mock, context) -> {
                            when(mock.connectionString(anyString())).thenReturn(mock);
                            when(mock.endpoint(anyString())).thenReturn(mock);
                            when(mock.credential(any(TokenCredential.class))).thenReturn(mock);
                            when(mock.buildClient()).thenReturn(mock(BlobServiceClient.class));
                        })) {

            BlobServiceClient client = AzureBlobClientFactory.getClient();

            assertNotNull(client);

            // Verify connection string path used
            verify(mockedBuilder.constructed().get(0)).connectionString(dummyConnString);
            verify(mockedBuilder.constructed().get(0)).buildClient();

            // Verify endpoint/credential path NOT used when connection string is present
            verify(mockedBuilder.constructed().get(0), never()).endpoint(anyString());
            verify(mockedBuilder.constructed().get(0), never()).credential(any(TokenCredential.class));

            // Credential builder should not be constructed at all
            assertTrue(mockedCredBuilder.constructed().isEmpty());
        }
    }
}
