// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

/**
 * Unit tests for FederatorConfigurationService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FederatorConfigurationService Tests")
class FederatorConfigurationServiceTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private static final String CACHE_KEY = "producer:default";
    private static final String CONSUMER_KEY = "consumer:default";

    @Mock
    private ManagementNodeDataHandler dataHandler;

    @Mock
    private InMemoryConfigurationStore configStore;

    private FederatorConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new FederatorConfigurationService(dataHandler, configStore);
    }

    @Test
    @DisplayName("Should return cached producer config")
    void testGetProducerConfiguration_CacheHit() throws ManagementNodeDataException {
        // Given
        final ProducerConfigDTO cached = createProducerConfig();
        when(configStore.get(CACHE_KEY, ProducerConfigDTO.class)).thenReturn(Optional.of(cached));

        // When
        final ProducerConfigDTO result = service.getProducerConfiguration();

        // Then
        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getProducerData(any());
    }

    @Test
    @DisplayName("Should fetch producer config on cache miss")
    void testGetProducerConfiguration_CacheMiss() throws ManagementNodeDataException {
        // Given
        final ProducerConfigDTO config = createProducerConfig();
        when(configStore.get(CACHE_KEY, ProducerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getProducerData(isNull())).thenReturn(config);

        // When
        final ProducerConfigDTO result = service.getProducerConfiguration();

        // Then
        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler).getProducerData(isNull());
        verify(configStore).store(CACHE_KEY, config);
    }

    @Test
    @DisplayName("Should return cached consumer config")
    void testGetConsumerConfiguration_CacheHit() throws ManagementNodeDataException {
        // Given
        final ConsumerConfigDTO cached = createConsumerConfig();
        when(configStore.get(CONSUMER_KEY, ConsumerConfigDTO.class)).thenReturn(Optional.of(cached));

        // When
        final ConsumerConfigDTO result = service.getConsumerConfiguration();

        // Then
        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getConsumerData(any());
    }

    @Test
    @DisplayName("Should fetch consumer config on cache miss")
    void testGetConsumerConfiguration_CacheMiss() throws ManagementNodeDataException {
        // Given
        final ConsumerConfigDTO config = createConsumerConfig();
        when(configStore.get(CONSUMER_KEY, ConsumerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getConsumerData(isNull())).thenReturn(config);

        // When
        final ConsumerConfigDTO result = service.getConsumerConfiguration();

        // Then
        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler).getConsumerData(isNull());
        verify(configStore).store(CONSUMER_KEY, config);
    }

    @Test
    @DisplayName("Should clear cache")
    void testClearCache() {
        // When
        service.clearCache();

        // Then
        verify(configStore).clearCache();
    }

    @Test
    @DisplayName("Should refresh configurations")
    void testRefreshConfigurations() throws ManagementNodeDataException {
        // Given
        final ProducerConfigDTO producer = createProducerConfig();
        final ConsumerConfigDTO consumer = createConsumerConfig();
        when(dataHandler.getProducerData(isNull())).thenReturn(producer);
        when(dataHandler.getConsumerData(isNull())).thenReturn(consumer);

        // When
        service.refreshConfigurations();

        // Then
        verify(configStore).clearCache();
        verify(dataHandler).getProducerData(isNull());
        verify(dataHandler).getConsumerData(isNull());
        verify(configStore).store(CACHE_KEY, producer);
        verify(configStore).store(CONSUMER_KEY, consumer);
    }

    @Test
    @DisplayName("Should throw exception on producer fetch error")
    void testGetProducerConfiguration_ThrowsException() throws ManagementNodeDataException {
        // Given
        when(configStore.get(CACHE_KEY, ProducerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getProducerData(isNull())).thenThrow(new ManagementNodeDataException("Failed"));

        // When/Then
        final ManagementNodeDataException ex =
                assertThrows(ManagementNodeDataException.class, () -> service.getProducerConfiguration());
        assertTrue(ex.getMessage().contains("Failed"));
    }

    @Test
    @DisplayName("Should throw exception on consumer fetch error")
    void testGetConsumerConfiguration_ThrowsException() throws ManagementNodeDataException {
        // Given
        when(configStore.get(CONSUMER_KEY, ConsumerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getConsumerData(isNull())).thenThrow(new ManagementNodeDataException("Failed"));

        // When/Then
        final ManagementNodeDataException ex =
                assertThrows(ManagementNodeDataException.class, () -> service.getConsumerConfiguration());
        assertTrue(ex.getMessage().contains("Failed"));
    }

    @Test
    @DisplayName("Should throw NPE for null dependencies")
    void testConstructor_NullDependencies() {
        // Test null data handler
        assertThrows(NullPointerException.class, () -> new FederatorConfigurationService(null, configStore));

        // Test null config store
        assertThrows(NullPointerException.class, () -> new FederatorConfigurationService(dataHandler, null));
    }

    private ProducerConfigDTO createProducerConfig() {
        final ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }

    private ConsumerConfigDTO createConsumerConfig() {
        final ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }
}
