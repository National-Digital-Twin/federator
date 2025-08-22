package uk.gov.dbt.ndtp.federator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FederatorConfigurationService.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@ExtendWith(MockitoExtension.class)
class FederatorConfigurationServiceTest {

    private static final String CLIENT_ID = "FEDERATOR_BCC";

    @Mock
    private ManagementNodeDataHandler dataHandler;
    @Mock
    private InMemoryConfigurationStore configStore;

    private FederatorConfigurationService service;

    /**
     * Sets up test fixtures before each test.
     */
    @BeforeEach
    void setUp() {
        service = new FederatorConfigurationService(dataHandler, configStore);
    }

    /**
     * Tests retrieving producer configuration from cache.
     */
    @Test
    void testGetProducerConfiguration_CacheHit() throws IOException {
        final ProducerConfigDTO cachedConfig = createProducerConfig();
        when(configStore.getProducerConfig(null)).thenReturn(cachedConfig);

        final ProducerConfigDTO result = service.getProducerConfiguration();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getProducerData();
    }

    /**
     * Tests retrieving producer configuration from management node.
     */
    @Test
    void testGetProducerConfiguration_CacheMiss() throws IOException {
        when(configStore.getProducerConfig(null)).thenReturn(null);
        final ProducerConfigDTO config = createProducerConfig();
        when(dataHandler.getProducerData()).thenReturn(config);

        final ProducerConfigDTO result = service.getProducerConfiguration();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler).getProducerData();
        verify(configStore).storeProducerConfig(CLIENT_ID, config);
    }

    /**
     * Tests retrieving consumer configuration from cache.
     */
    @Test
    void testGetConsumerConfiguration_CacheHit() throws IOException {
        final ConsumerConfigDTO cachedConfig = createConsumerConfig();
        when(configStore.getConsumerConfig(null)).thenReturn(cachedConfig);

        final ConsumerConfigDTO result = service.getConsumerConfiguration();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getConsumerData();
    }

    /**
     * Tests configuration refresh functionality.
     */
    @Test
    void testRefreshConfigurations() throws IOException {
        final ProducerConfigDTO producerConfig = createProducerConfig();
        final ConsumerConfigDTO consumerConfig = createConsumerConfig();
        when(dataHandler.getProducerData()).thenReturn(producerConfig);
        when(dataHandler.getConsumerData()).thenReturn(consumerConfig);

        service.refreshConfigurations();

        verify(configStore).clearCache();
        verify(dataHandler).getProducerData();
        verify(dataHandler).getConsumerData();
    }

    /**
     * Tests cache clearing functionality.
     */
    @Test
    void testClearCache() {
        service.clearCache();
        verify(configStore).clearCache();
    }

    /**
     * Tests retry mechanism on IOException.
     */
    @Test
    void testGetProducerConfiguration_RetryOnFailure() throws IOException {
        when(configStore.getProducerConfig(null)).thenReturn(null);
        final ProducerConfigDTO config = createProducerConfig();
        when(dataHandler.getProducerData())
                .thenThrow(new IOException("First attempt"))
                .thenReturn(config);

        final ProducerConfigDTO result = service.getProducerConfiguration();

        assertNotNull(result);
        verify(dataHandler, times(2)).getProducerData();
    }

    /**
     * Tests maximum retry attempts exceeded.
     */
    @Test
    void testGetProducerConfiguration_MaxRetriesExceeded() throws IOException {
        when(configStore.getProducerConfig(null)).thenReturn(null);
        when(dataHandler.getProducerData()).thenThrow(new IOException("Failed"));

        assertThrows(IOException.class, () -> service.getProducerConfiguration());
        verify(dataHandler, times(3)).getProducerData();
    }

    /**
     * Creates mock producer configuration.
     */
    private ProducerConfigDTO createProducerConfig() {
        final ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }

    /**
     * Creates mock consumer configuration.
     */
    private ConsumerConfigDTO createConsumerConfig() {
        final ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }
}