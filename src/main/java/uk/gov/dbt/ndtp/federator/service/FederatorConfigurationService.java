package uk.gov.dbt.ndtp.federator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

import java.io.IOException;

/**
 * Service for managing federator configurations with caching support.
 * Orchestrates configuration retrieval from management node with automatic caching.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@Slf4j
@RequiredArgsConstructor
public class FederatorConfigurationService {

    private static final String CONFIG_FETCH_ERROR = "Failed to fetch configuration: {}";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final ManagementNodeDataHandler dataHandler;
    private final InMemoryConfigurationStore configStore;
    private final JwtTokenService tokenService;

    /**
     * Retrieves producer configuration with caching support.
     * First attempts to retrieve from cache, falls back to management node if not cached.
     *
     * @param jwtToken the JWT token for authentication
     * @return ProducerConfigDTO containing producer configuration
     * @throws IOException if configuration retrieval fails after all retries
     */
    public ProducerConfigDTO getProducerConfiguration(final String jwtToken) throws IOException {
        if (!tokenService.isTokenValid(jwtToken)) {
            throw new IllegalArgumentException("Invalid or expired JWT token");
        }
        final String clientId = tokenService.extractClientId(jwtToken);
        ProducerConfigDTO cached = configStore.getProducerConfig(clientId);
        if (cached != null) {
            log.debug("Returning cached producer configuration for client: {}", clientId);
            return cached;
        }
        log.info("Fetching producer configuration from management node");
        final ProducerConfigDTO config = fetchWithRetry(() -> dataHandler.getProducerData(jwtToken,null));
        configStore.storeProducerConfig(clientId, config);
        return config;
    }

    /**
     * Retrieves consumer configuration with caching support.
     * First attempts to retrieve from cache, falls back to management node if not cached.
     *
     * @param jwtToken the JWT token for authentication
     * @return ConsumerConfigDTO containing consumer configuration
     * @throws IOException if configuration retrieval fails after all retries
     */
    public ConsumerConfigDTO getConsumerConfiguration(final String jwtToken) throws IOException {
        if (!tokenService.isTokenValid(jwtToken)) {
            throw new IllegalArgumentException("Invalid or expired JWT token");
        }
        final String clientId = tokenService.extractClientId(jwtToken);
        ConsumerConfigDTO cached = configStore.getConsumerConfig(clientId);
        if (cached != null) {
            log.debug("Returning cached consumer configuration for client: {}", clientId);
            return cached;
        }
        log.info("Fetching consumer configuration from management node");
        final ConsumerConfigDTO config = fetchWithRetry(() -> dataHandler.getConsumerData(jwtToken, null));
        configStore.storeConsumerConfig(clientId, config);
        return config;
    }

    /**
     * Refreshes all configurations by clearing cache and fetching fresh data.
     *
     * @param jwtToken the JWT token for authentication
     * @throws IOException if configuration refresh fails
     */
    public void refreshConfigurations(final String jwtToken) throws IOException {
        if (!tokenService.isTokenValid(jwtToken)) {
            throw new IllegalArgumentException("Invalid or expired JWT token");
        }
        log.info("Refreshing all configurations");
        configStore.clearCache();
        getProducerConfiguration(jwtToken);
        getConsumerConfiguration(jwtToken);
        log.info("Configuration refresh completed");
    }

    /**
     * Executes configuration fetch with retry logic.
     *
     * @param fetcher the configuration fetch operation
     * @param <T> the type of configuration
     * @return the fetched configuration
     * @throws IOException if all retry attempts fail
     */
    private <T> T fetchWithRetry(final ConfigFetcher<T> fetcher) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return fetcher.fetch();
            } catch (final IOException | InterruptedException e) {
                lastException = new IOException("Fetch attempt " + attempt + " failed", e);
                log.warn(CONFIG_FETCH_ERROR, e.getMessage());
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
            }
        }
        throw lastException;
    }

    /**
     * Functional interface for configuration fetch operations.
     *
     * @param <T> the type of configuration to fetch
     */
    @FunctionalInterface
    private interface ConfigFetcher<T> {
        T fetch() throws IOException, InterruptedException;
    }
}