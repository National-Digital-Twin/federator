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

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final ManagementNodeDataHandler dataHandler;
    private final InMemoryConfigurationStore configStore;

    /**
     * Retrieves producer configuration with caching support.
     *
     * @return ProducerConfigDTO containing producer configuration
     * @throws IOException if configuration retrieval fails after all retries
     */
    public ProducerConfigDTO getProducerConfiguration() throws IOException {
        // Note: extractClientIdFromCache always returns null in current implementation
        // Keeping for future enhancement when client ID caching is implemented
        final ProducerConfigDTO cached = configStore.getProducerConfig(null);
        if (cached != null) {
            log.debug("Returning cached producer configuration");
            return cached;
        }

        log.info("Fetching producer configuration from management node");
        final ProducerConfigDTO config = fetchWithRetry(() -> dataHandler.getProducerData());

        if (config != null && config.getClientId() != null) {
            configStore.storeProducerConfig(config.getClientId(), config);
        }
        return config;
    }

    /**
     * Retrieves consumer configuration with caching support.
     *
     * @return ConsumerConfigDTO containing consumer configuration
     * @throws IOException if configuration retrieval fails after all retries
     */
    public ConsumerConfigDTO getConsumerConfiguration() throws IOException {
        final ConsumerConfigDTO cached = configStore.getConsumerConfig(null);
        if (cached != null) {
            log.debug("Returning cached consumer configuration");
            return cached;
        }

        log.info("Fetching consumer configuration from management node");
        final ConsumerConfigDTO config = fetchWithRetry(dataHandler::getConsumerData);

        if (config != null && config.getClientId() != null) {
            configStore.storeConsumerConfig(config.getClientId(), config);
        }
        return config;
    }

    /**
     * Refreshes all configurations by clearing cache and fetching fresh data.
     *
     * @throws IOException if configuration refresh fails
     */
    public void refreshConfigurations() throws IOException {
        log.info("Refreshing all configurations");
        configStore.clearCache();
        getProducerConfiguration();
        getConsumerConfiguration();
        log.info("Configuration refresh completed");
    }

    /**
     * Clears the configuration cache.
     */
    public void clearCache() {
        log.info("Clearing configuration cache");
        configStore.clearCache();
    }

    /**
     * Executes configuration fetch with retry logic.
     *
     * @param fetcher the configuration fetch operation
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
                log.warn("Failed to fetch configuration: {}", e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry sleep interrupted", ie);
                    }
                }
            }
        }
        throw lastException;
    }

    /**
     * Functional interface for configuration fetch operations.
     */
    @FunctionalInterface
    private interface ConfigFetcher<T> {
        T fetch() throws IOException, InterruptedException;
    }
}