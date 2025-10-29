// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.service.config;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.common.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Service for managing federator configurations with caching.
 */
@Slf4j
public class ConsumerConfigService implements ConfigService<ConsumerConfigDTO> {

    private static final String CONSUMER_KEY_PREFIX = "consumer:";
    private static final String DEFAULT_KEY = "default";
    private static final String CONSUMER_ID_PROP = "federator.consumer.id";

    private final ManagementNodeDataHandler dataHandler;
    private final InMemoryConfigurationStore configStore;
    private final String configuredConsumerId;

    /**
     * Constructs service with required dependencies.
     *
     * @param dataHandler handler for management node communication
     * @param configStore in-memory cache for configurations
     * @throws NullPointerException if any parameter is null
     */
    public ConsumerConfigService(
            final ManagementNodeDataHandler dataHandler, final InMemoryConfigurationStore configStore) {
        this.dataHandler = Objects.requireNonNull(dataHandler, "Data handler must not be null");
        this.configStore = Objects.requireNonNull(configStore, "Config store must not be null");
        this.configuredConsumerId = PropertyUtil.getPropertyValue(CONSUMER_ID_PROP, null);
        log.info("Service initialized - Consumer ID: {}", configuredConsumerId != null ? configuredConsumerId : "all");
    }

    // ----------------- ConfigService contract implementations -----------------
    @Override
    public InMemoryConfigurationStore getConfigStore() {
        return configStore;
    }

    @Override
    public String getKeyPrefix() {
        return CONSUMER_KEY_PREFIX;
    }

    @Override
    public String getConfiguredClientId() {
        return configuredConsumerId;
    }

    @Override
    public Class<ConsumerConfigDTO> getDtoClass() {
        return ConsumerConfigDTO.class;
    }

    @Override
    public ConsumerConfigDTO fetchConfiguration() {
        return dataHandler.getConsumerData(configuredConsumerId);
    }

    // Backwards-compatible convenience methods
    public ConsumerConfigDTO getConsumerConfiguration() {
        return getConfiguration();
    }
}
