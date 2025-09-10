// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.service;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

/**
 * Service for managing federator configurations with caching.
 */
@Slf4j
public class ProducerConsumerConfigService {

    private static final String PRODUCER_KEY_PREFIX = "producer:";
    private static final String CONSUMER_KEY_PREFIX = "consumer:";
    private static final String DEFAULT_KEY = "default";
    private static final String PRODUCER_ID_PROP = "federator.producer.id";
    private static final String CONSUMER_ID_PROP = "federator.consumer.id";

    private final ManagementNodeDataHandler dataHandler;
    private final InMemoryConfigurationStore configStore;
    private final String configuredProducerId;
    private final String configuredConsumerId;

    /**
     * Constructs service with required dependencies.
     *
     * @param dataHandler handler for management node communication
     * @param configStore in-memory cache for configurations
     * @throws NullPointerException if any parameter is null
     */
    public ProducerConsumerConfigService(
            final ManagementNodeDataHandler dataHandler, final InMemoryConfigurationStore configStore) {
        this.dataHandler = Objects.requireNonNull(dataHandler, "Data handler must not be null");
        this.configStore = Objects.requireNonNull(configStore, "Config store must not be null");

        this.configuredProducerId = PropertyUtil.getPropertyValue(PRODUCER_ID_PROP, null);
        this.configuredConsumerId = PropertyUtil.getPropertyValue(CONSUMER_ID_PROP, null);

        log.info(
                "Service initialized - Producer ID: {}, Consumer ID: {}",
                configuredProducerId != null ? configuredProducerId : "all",
                configuredConsumerId != null ? configuredConsumerId : "all");
    }

    /**
     * Gets producer configuration from cache or fetches from node.
     *
     * @return ProducerConfigDTO containing producer configuration
     * @throws ManagementNodeDataException if unable to fetch
     */
    public ProducerConfigDTO getProducerConfiguration() throws ManagementNodeDataException {
        final String cacheKey = buildCacheKey(PRODUCER_KEY_PREFIX, configuredProducerId);

        // Check cache first
        final Optional<ProducerConfigDTO> cached = configStore.get(cacheKey, ProducerConfigDTO.class);
        if (cached.isPresent()) {
            log.debug("Returning cached producer configuration");
            return cached.get();
        }

        // Fetch from management node
        log.info("Fetching producer configuration from node");
        final ProducerConfigDTO config = dataHandler.getProducerData(configuredProducerId);

        // Store in cache
        configStore.store(cacheKey, config);
        return config;
    }

    /**
     * Gets consumer configuration from cache or fetches from node.
     *
     * @return ConsumerConfigDTO containing consumer configuration
     * @throws ManagementNodeDataException if unable to fetch
     */
    public ConsumerConfigDTO getConsumerConfiguration() throws ManagementNodeDataException {
        final String cacheKey = buildCacheKey(CONSUMER_KEY_PREFIX, configuredConsumerId);

        // Check cache first
        final Optional<ConsumerConfigDTO> cached = configStore.get(cacheKey, ConsumerConfigDTO.class);
        if (cached.isPresent()) {
            log.debug("Returning cached consumer configuration");
            return cached.get();
        }

        // Fetch from management node
        log.info("Fetching consumer configuration from node");
        final ConsumerConfigDTO config = dataHandler.getConsumerData(configuredConsumerId);

        // Store in cache
        configStore.store(cacheKey, config);
        return config;
    }

    /**
     * Refreshes configurations by clearing cache and fetching.
     *
     * @throws ManagementNodeDataException if unable to fetch
     */
    public void refreshConfigurations() throws ManagementNodeDataException {
        log.info("Refreshing configurations");
        configStore.clearCache();
        getProducerConfiguration();
        getConsumerConfiguration();
        log.info("Configurations refreshed successfully");
    }

    /**
     * Clears the configuration cache.
     */
    public void clearCache() {
        configStore.clearCache();
    }

    /**
     * Builds cache key for configuration storage.
     *
     * @param prefix key prefix for configuration type
     * @param clientId client identifier, may be null
     * @return formatted cache key
     */
    private String buildCacheKey(final String prefix, final String clientId) {
        return prefix + (clientId != null ? clientId : DEFAULT_KEY);
    }
}
