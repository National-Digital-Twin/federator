// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.service;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Service for managing federator configurations with caching support.
 */
@Slf4j
public class FederatorConfigurationService {

    private final ManagementNodeDataHandler dataHandler;
    private final InMemoryConfigurationStore configStore;
    private final Optional<String> configuredProducerId;
    private final Optional<String> configuredConsumerId;
    private final int maxRetries;
    private final long retryDelay;

    /**
     * Constructs service with dependencies and loads configuration.
     *
     * @param dataHandler handler for management node communication, must not be null
     * @param configStore in-memory cache for configurations, must not be null
     * @throws NullPointerException if any parameter is null
     */
    public FederatorConfigurationService(final ManagementNodeDataHandler dataHandler,
                                         final InMemoryConfigurationStore configStore) {
        this.dataHandler = Objects.requireNonNull(dataHandler);
        this.configStore = Objects.requireNonNull(configStore);
        this.maxRetries = PropertyUtil.getPropertyIntValue("federator.retry.max.attempts");
        this.retryDelay = PropertyUtil.getPropertyLongValue("federator.retry.delay.ms");

        this.configuredProducerId = loadOptionalProperty("federator.producer.id");
        this.configuredConsumerId = loadOptionalProperty("federator.consumer.id");

        log.info("Service initialized - Producer ID: {}, Consumer ID: {}",
                configuredProducerId.orElse("all"),
                configuredConsumerId.orElse("all"));
    }

    /**
     * Gets producer configuration from cache or fetches from management node.
     *
     * @return ProducerConfigDTO containing producer configuration, never null
     * @throws ManagementNodeDataException if unable to fetch configuration after all retry attempts
     */
    public ProducerConfigDTO getOrFetchProducerConfiguration() throws ManagementNodeDataException {
        String cacheKey = configuredProducerId.orElse(null);
        ProducerConfigDTO cached = configStore.getProducerConfig(cacheKey);
        if (cached != null) {
            return cached;
        }

        ProducerConfigDTO config = retryOperation(() ->
                dataHandler.getProducerData(configuredProducerId));
        configStore.storeProducerConfig(
                config.getClientId() != null ? config.getClientId() : cacheKey, config);
        return config;
    }

    /**
     * Gets consumer configuration from cache or fetches from management node.
     *
     * @return ConsumerConfigDTO containing consumer configuration, never null
     * @throws ManagementNodeDataException if unable to fetch configuration after all retry attempts
     */
    public ConsumerConfigDTO getOrFetchConsumerConfiguration() throws ManagementNodeDataException {
        String cacheKey = configuredConsumerId.orElse(null);
        ConsumerConfigDTO cached = configStore.getConsumerConfig(cacheKey);
        if (cached != null) {
            return cached;
        }

        ConsumerConfigDTO config = retryOperation(() ->
                dataHandler.getConsumerData(configuredConsumerId));
        configStore.storeConsumerConfig(
                config.getClientId() != null ? config.getClientId() : cacheKey, config);
        return config;
    }

    /**
     * Refreshes all configurations by clearing cache and fetching fresh data.
     *
     * @throws ManagementNodeDataException if unable to fetch either configuration
     */
    public void refreshConfigurations() throws ManagementNodeDataException {
        configStore.clearCache();
        getOrFetchProducerConfiguration();
        getOrFetchConsumerConfiguration();
        log.info("Configurations refreshed");
    }

    /**
     * Clears all cached configurations.
     */
    public void clearCache() {
        configStore.clearCache();
    }

    /**
     * Executes operation with retry logic using exponential backoff.
     *
     * @param operation callable operation to execute
     * @param <T> type of result returned by operation
     * @return result of successful operation execution
     * @throws ManagementNodeDataException if all retry attempts fail
     */
    private <T> T retryOperation(final Callable<T> operation) throws ManagementNodeDataException {
        ManagementNodeDataException lastError = null;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastError = e instanceof ManagementNodeDataException mnde ? mnde :
                        new ManagementNodeDataException("Operation failed", e);
                if (i < maxRetries) {
                    sleep(retryDelay * i);
                }
            }
        }
        throw new ManagementNodeDataException("Failed after " + maxRetries + " attempts", lastError);
    }

    /**
     * Loads optional property from configuration.
     *
     * @param key property key
     * @return Optional containing value if present and non-empty
     */
    private Optional<String> loadOptionalProperty(final String key) {
        try {
            String value = PropertyUtil.getPropertyValue(key, "");
            return value.trim().isEmpty() ? Optional.empty() : Optional.of(value.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Sleeps for specified milliseconds, properly handling interruption.
     *
     * @param millis milliseconds to sleep
     */
    private void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}