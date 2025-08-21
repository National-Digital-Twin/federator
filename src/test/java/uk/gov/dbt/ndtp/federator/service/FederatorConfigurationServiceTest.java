package uk.gov.dbt.ndtp.federator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FederatorConfigurationService}.
 *
 * <p>This test class validates the orchestration of configuration
 * retrieval with caching, retry logic, and token validation.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Federator Configuration Service Tests")
class FederatorConfigurationServiceTest {

    private static final String VALID_JWT = "valid-jwt-token";
    private static final String INVALID_JWT = "invalid-jwt-token";
    private static final String CLIENT_ID = "FEDERATOR_BCC";
    private static final String CONSUMER_ID = "FEDERATOR_HEG";
    private static final String ERROR_MESSAGE = "Network error";

    @Mock
    private ManagementNodeDataHandler dataHandler;

    @Mock
    private InMemoryConfigurationStore configStore;

    @Mock
    private JwtTokenService tokenService;

    private FederatorConfigurationService service;

    /**
     * Sets up test fixtures before each test method.
     * Initializes the service with mocked dependencies.
     */
    @BeforeEach
    void setUp() {
        service = new FederatorConfigurationService(dataHandler, configStore, tokenService);
    }

    /**
     * Tests configuration retrieval with caching behavior.
     * Validates cache hit, cache miss, and remote fetching scenarios.
     *
     * @throws Exception if retrieval fails
     */
    @Test
    @DisplayName("Should handle caching correctly for configurations")
    void testConfigurationCaching() throws Exception {
        final ProducerConfigDTO cachedProducer = ProducerConfigDTO.builder()
                .clientId(CLIENT_ID).producers(List.of()).build();
        final ProducerConfigDTO remoteProducer = ProducerConfigDTO.builder()
                .clientId(CLIENT_ID).producers(List.of()).build();
        final ConsumerConfigDTO remoteConsumer = ConsumerConfigDTO.builder()
                .clientId(CONSUMER_ID).producers(List.of()).build();

        when(tokenService.isTokenValid(VALID_JWT)).thenReturn(true);
        when(tokenService.extractClientId(VALID_JWT)).thenReturn(CLIENT_ID);
        when(configStore.getProducerConfig(CLIENT_ID)).thenReturn(cachedProducer).thenReturn(null);
        when(dataHandler.getProducerData(VALID_JWT, null)).thenReturn(remoteProducer);
        when(configStore.getConsumerConfig(CLIENT_ID)).thenReturn(null);
        when(dataHandler.getConsumerData(VALID_JWT, null)).thenReturn(remoteConsumer);

        // Test cache hit - should return cached producer
        assertEquals(cachedProducer, service.getProducerConfiguration(VALID_JWT), "Should return cached");
        // Verify that dataHandler was never called with any arguments (using matchers for all args)
        verify(dataHandler, never()).getProducerData(anyString(), isNull());

        // Test cache miss - should fetch from remote
        assertEquals(remoteProducer, service.getProducerConfiguration(VALID_JWT), "Should fetch remote");
        verify(dataHandler, times(1)).getProducerData(VALID_JWT, null);
        verify(configStore, times(1)).storeProducerConfig(CLIENT_ID, remoteProducer);

        // Test consumer configuration fetch
        assertEquals(remoteConsumer, service.getConsumerConfiguration(VALID_JWT), "Should fetch consumer");
        verify(configStore, times(1)).storeConsumerConfig(CLIENT_ID, remoteConsumer);
    }

    /**
     * Tests error handling and retry logic for failed requests.
     * Validates token validation and retry attempts.
     *
     * @throws Exception if test setup fails
     */
    @Test
    @DisplayName("Should handle errors and retry appropriately")
    void testErrorHandlingAndRetry() throws Exception {
        // Test invalid token handling
        when(tokenService.isTokenValid(INVALID_JWT)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.getProducerConfiguration(INVALID_JWT),
                "Should throw for invalid token");

        assertThrows(IllegalArgumentException.class,
                () -> service.getConsumerConfiguration(INVALID_JWT),
                "Should throw for invalid token");

        // Test retry logic with failures
        when(tokenService.isTokenValid(VALID_JWT)).thenReturn(true);
        when(tokenService.extractClientId(VALID_JWT)).thenReturn(CLIENT_ID);
        when(configStore.getProducerConfig(CLIENT_ID)).thenReturn(null);

        when(dataHandler.getProducerData(VALID_JWT, null))
                .thenThrow(new IOException(ERROR_MESSAGE))
                .thenThrow(new IOException(ERROR_MESSAGE))
                .thenThrow(new IOException(ERROR_MESSAGE));

        assertThrows(IOException.class,
                () -> service.getProducerConfiguration(VALID_JWT),
                "Should throw after max retries");

        verify(dataHandler, times(3)).getProducerData(VALID_JWT, null);

        // Reset mocks for refresh test
        reset(dataHandler, configStore);

        // Test successful refresh
        final ProducerConfigDTO successProducer = ProducerConfigDTO.builder()
                .clientId(CLIENT_ID).producers(List.of()).build();
        final ConsumerConfigDTO successConsumer = ConsumerConfigDTO.builder()
                .clientId(CLIENT_ID).producers(List.of()).build();

        when(configStore.getProducerConfig(CLIENT_ID)).thenReturn(null);
        when(configStore.getConsumerConfig(CLIENT_ID)).thenReturn(null);
        when(dataHandler.getProducerData(VALID_JWT, null)).thenReturn(successProducer);
        when(dataHandler.getConsumerData(VALID_JWT, null)).thenReturn(successConsumer);

        assertDoesNotThrow(() -> service.refreshConfigurations(VALID_JWT),
                "Refresh should succeed after resetting mocks");

        verify(configStore, times(1)).clearCache();
        verify(dataHandler, times(1)).getProducerData(VALID_JWT, null);
        verify(dataHandler, times(1)).getConsumerData(VALID_JWT, null);
    }
}