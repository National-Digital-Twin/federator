/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.lang.reflect.Field;
import java.util.Properties;
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
            // Ignore if we can't reset it
        }
    }

    @Test
    void testGetClient_ThrowsExceptionWhenNoConnectionString() {
        // PropertyUtil is cleared, so connection string will be null
        // However, PropertyUtil.getPropertyValue might throw PropertyUtilException if not initialized
        // instead of ConfigurationException.
        // We should ensure it's initialized but empty, or catch PropertyUtilException

        Properties props = new Properties();
        PropertyUtil.overrideSystemProperties(props);

        assertThrows(ConfigurationException.class, AzureBlobClientFactory::getClient);
    }

    @Test
    void testGetClient_Success() {
        String dummyConnString =
                "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net";

        Properties props = new Properties();
        props.setProperty("azure.storage.connection.string", dummyConnString);
        PropertyUtil.overrideSystemProperties(props);

        try (MockedConstruction<BlobServiceClientBuilder> mockedBuilder =
                mockConstruction(BlobServiceClientBuilder.class, (mock, context) -> {
                    when(mock.connectionString(anyString())).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mock(BlobServiceClient.class));
                })) {

            BlobServiceClient client = AzureBlobClientFactory.getClient();

            assertNotNull(client);
            verify(mockedBuilder.constructed().get(0)).connectionString(dummyConnString);
            verify(mockedBuilder.constructed().get(0)).buildClient();
        } finally {
            PropertyUtil.clear();
        }
    }
}
