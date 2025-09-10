// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
// language: java
package uk.gov.dbt.ndtp.federator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProducerConsumerConfigService Tests")
class ProducerConsumerConfigServiceTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private static final String PRODUCER_KEY = "producer:" + CLIENT_ID;
    private static final String CONSUMER_KEY = "consumer:" + CLIENT_ID;

    private MockedStatic<PropertyUtil> propertyUtilMock;

    @Mock
    private ManagementNodeDataHandler dataHandler;

    @Mock
    private InMemoryConfigurationStore configStore;

    private ProducerConsumerConfigService service;

    @BeforeEach
    void setUp() {
        propertyUtilMock = mockStatic(PropertyUtil.class);
        propertyUtilMock
                .when(() -> PropertyUtil.getPropertyValue("federator.producer.id", null))
                .thenReturn(CLIENT_ID);
        propertyUtilMock
                .when(() -> PropertyUtil.getPropertyValue("federator.consumer.id", null))
                .thenReturn(CLIENT_ID);

        service = new ProducerConsumerConfigService(dataHandler, configStore);
    }

    @AfterEach
    void tearDown() {
        propertyUtilMock.close();
    }

    @Test
    @DisplayName("Should return cached producer config")
    void testGetProducerConfiguration_CacheHit() throws ManagementNodeDataException {
        final ProducerConfigDTO cached = createProducerConfig();
        when(configStore.get(PRODUCER_KEY, ProducerConfigDTO.class)).thenReturn(Optional.of(cached));

        final ProducerConfigDTO result = service.getProducerConfiguration();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getProducerData(any());
    }

    @Test
    @DisplayName("Should fetch producer config on cache miss")
    void testGetProducerConfiguration_CacheMiss() throws ManagementNodeDataException {
        final ProducerConfigDTO config = createProducerConfig();
        when(configStore.get(PRODUCER_KEY, ProducerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getProducerData(CLIENT_ID)).thenReturn(config);

        final ProducerConfigDTO result = service.getProducerConfiguration();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler).getProducerData(CLIENT_ID);
        verify(configStore).store(PRODUCER_KEY, config);
    }

    @Test
    @DisplayName("Should return cached consumer config")
    void testGetConsumerConfiguration_CacheHit() throws ManagementNodeDataException {
        final ConsumerConfigDTO cached = createConsumerConfig();
        when(configStore.get(CONSUMER_KEY, ConsumerConfigDTO.class)).thenReturn(Optional.of(cached));

        final ConsumerConfigDTO result = service.getConsumerConfiguration();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler, never()).getConsumerData(any());
    }

    @Test
    @DisplayName("Should fetch consumer config on cache miss")
    void testGetConsumerConfiguration_CacheMiss() throws ManagementNodeDataException {
        final ConsumerConfigDTO config = createConsumerConfig();
        when(configStore.get(CONSUMER_KEY, ConsumerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getConsumerData(CLIENT_ID)).thenReturn(config);

        final ConsumerConfigDTO result = service.getConsumerConfiguration();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(dataHandler).getConsumerData(CLIENT_ID);
        verify(configStore).store(CONSUMER_KEY, config);
    }

    @Test
    @DisplayName("Should clear cache")
    void testClearCache() {
        service.clearCache();
        verify(configStore).clearCache();
    }

    @Test
    @DisplayName("Should refresh configurations")
    void testRefreshConfigurations() throws ManagementNodeDataException {
        final ProducerConfigDTO producer = createProducerConfig();
        final ConsumerConfigDTO consumer = createConsumerConfig();

        // Ensure cache misses so refresh fetches and stores both configs
        when(configStore.get(PRODUCER_KEY, ProducerConfigDTO.class)).thenReturn(Optional.empty());
        when(configStore.get(CONSUMER_KEY, ConsumerConfigDTO.class)).thenReturn(Optional.empty());

        when(dataHandler.getProducerData(CLIENT_ID)).thenReturn(producer);
        when(dataHandler.getConsumerData(CLIENT_ID)).thenReturn(consumer);

        service.refreshConfigurations();

        verify(configStore).clearCache();
        verify(dataHandler).getProducerData(CLIENT_ID);
        verify(dataHandler).getConsumerData(CLIENT_ID);
        verify(configStore).store(PRODUCER_KEY, producer);
        verify(configStore).store(CONSUMER_KEY, consumer);
    }

    @Test
    @DisplayName("Should throw exception on producer fetch error")
    void testGetProducerConfiguration_ThrowsException() throws ManagementNodeDataException {
        when(configStore.get(PRODUCER_KEY, ProducerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getProducerData(CLIENT_ID)).thenThrow(new ManagementNodeDataException("Failed"));

        final ManagementNodeDataException ex =
                assertThrows(ManagementNodeDataException.class, () -> service.getProducerConfiguration());
        assertTrue(ex.getMessage().contains("Failed"));
    }

    @Test
    @DisplayName("Should throw exception on consumer fetch error")
    void testGetConsumerConfiguration_ThrowsException() throws ManagementNodeDataException {
        when(configStore.get(CONSUMER_KEY, ConsumerConfigDTO.class)).thenReturn(Optional.empty());
        when(dataHandler.getConsumerData(CLIENT_ID)).thenThrow(new ManagementNodeDataException("Failed"));

        final ManagementNodeDataException ex =
                assertThrows(ManagementNodeDataException.class, () -> service.getConsumerConfiguration());
        assertTrue(ex.getMessage().contains("Failed"));
    }

    @Test
    @DisplayName("Should throw NPE for null dependencies")
    void testConstructor_NullDependencies() {
        assertThrows(NullPointerException.class, () -> new ProducerConsumerConfigService(null, configStore));
        assertThrows(NullPointerException.class, () -> new ProducerConsumerConfigService(dataHandler, null));
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
