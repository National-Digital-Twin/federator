// SPDX-License-Identifier: Apache-2.0
package uk.gov.dbt.ndtp.federator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.CommonPropertiesLoader;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FederatorConfigurationService.
 */
@ExtendWith(MockitoExtension.class)
class FederatorConfigurationServiceTest {

    private static final String CLIENT_ID = "TEST_CLIENT";

    @Mock private ManagementNodeDataHandler dataHandler;
    @Mock private InMemoryConfigurationStore configStore;
    private FederatorConfigurationService service;

    @BeforeEach
    void setUp() {
        CommonPropertiesLoader.loadTestProperties();
        service = new FederatorConfigurationService(dataHandler, configStore);
    }

    @Test
    void testGetOrFetchProducerConfiguration_CacheHit() throws ManagementNodeDataException {
        ProducerConfigDTO cached = createProducerConfig();
        when(configStore.getProducerConfig(null)).thenReturn(cached);

        ProducerConfigDTO result = service.getOrFetchProducerConfiguration();

        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getProducerData(any());
    }

    @Test
    void testGetOrFetchProducerConfiguration_CacheMiss() throws ManagementNodeDataException {
        ProducerConfigDTO config = createProducerConfig();
        when(configStore.getProducerConfig(null)).thenReturn(null);
        when(dataHandler.getProducerData(Optional.empty())).thenReturn(config);

        ProducerConfigDTO result = service.getOrFetchProducerConfiguration();

        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler).getProducerData(Optional.empty());
        verify(configStore).storeProducerConfig(CLIENT_ID, config);
    }

    @Test
    void testGetOrFetchConsumerConfiguration_CacheHit() throws ManagementNodeDataException {
        ConsumerConfigDTO cached = createConsumerConfig();
        when(configStore.getConsumerConfig(null)).thenReturn(cached);

        ConsumerConfigDTO result = service.getOrFetchConsumerConfiguration();

        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getConsumerData(any());
    }

    @Test
    void testGetOrFetchConsumerConfiguration_CacheMiss() throws ManagementNodeDataException {
        ConsumerConfigDTO config = createConsumerConfig();
        when(configStore.getConsumerConfig(null)).thenReturn(null);
        when(dataHandler.getConsumerData(Optional.empty())).thenReturn(config);

        ConsumerConfigDTO result = service.getOrFetchConsumerConfiguration();

        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler).getConsumerData(Optional.empty());
        verify(configStore).storeConsumerConfig(CLIENT_ID, config);
    }

    @Test
    void testRefreshConfigurations() throws ManagementNodeDataException {
        ProducerConfigDTO producerConfig = createProducerConfig();
        ConsumerConfigDTO consumerConfig = createConsumerConfig();
        when(configStore.getProducerConfig(null)).thenReturn(null);
        when(configStore.getConsumerConfig(null)).thenReturn(null);
        when(dataHandler.getProducerData(Optional.empty())).thenReturn(producerConfig);
        when(dataHandler.getConsumerData(Optional.empty())).thenReturn(consumerConfig);

        service.refreshConfigurations();

        verify(configStore).clearCache();
        verify(dataHandler).getProducerData(Optional.empty());
        verify(dataHandler).getConsumerData(Optional.empty());
    }

    @Test
    void testClearCache() {
        service.clearCache();
        verify(configStore).clearCache();
    }

    @Test
    void testRetryOnFailure() throws ManagementNodeDataException {
        ProducerConfigDTO config = createProducerConfig();
        when(configStore.getProducerConfig(null)).thenReturn(null);
        when(dataHandler.getProducerData(Optional.empty()))
                .thenThrow(new ManagementNodeDataException("First"))
                .thenReturn(config);

        ProducerConfigDTO result = service.getOrFetchProducerConfiguration();

        assertNotNull(result);
        verify(dataHandler, times(2)).getProducerData(Optional.empty());
    }

    @Test
    void testMaxRetriesExceeded() throws ManagementNodeDataException {
        when(configStore.getProducerConfig(null)).thenReturn(null);
        when(dataHandler.getProducerData(Optional.empty()))
                .thenThrow(new ManagementNodeDataException("Failed"));

        assertThrows(ManagementNodeDataException.class, () ->
                service.getOrFetchProducerConfiguration());
        verify(dataHandler, times(3)).getProducerData(Optional.empty());
    }

    @Test
    void testRetryOnNonManagementNodeException() throws Exception {
        ProducerConfigDTO config = createProducerConfig();
        when(configStore.getProducerConfig(null)).thenReturn(null);
        when(dataHandler.getProducerData(Optional.empty()))
                .thenThrow(new RuntimeException("Runtime error"))
                .thenReturn(config);

        ProducerConfigDTO result = service.getOrFetchProducerConfiguration();

        assertNotNull(result);
        verify(dataHandler, times(2)).getProducerData(Optional.empty());
    }

    private ProducerConfigDTO createProducerConfig() {
        ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }

    private ConsumerConfigDTO createConsumerConfig() {
        ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }
}