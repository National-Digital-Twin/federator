/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
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
        PropertyUtil.init("client.properties");
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
        // Ensure PropertyUtil is initialized but the property is missing
        PropertyUtil.clear();
        PropertyUtil.init("client.properties"); // This loads samplestring but we want to override it

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

        try (MockedConstruction<DefaultAzureCredentialBuilder> mockedCredBuilder =
                        mockConstruction(DefaultAzureCredentialBuilder.class, (mock, context) -> {
                            when(mock.build()).thenReturn(mock(DefaultAzureCredential.class));
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

        try (MockedConstruction<DefaultAzureCredentialBuilder> mockedCredBuilder =
                mockConstruction(DefaultAzureCredentialBuilder.class, (mock, context) -> {
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
}
