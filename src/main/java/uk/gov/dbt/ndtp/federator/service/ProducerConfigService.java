// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.service;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

/**
 * Service for managing producer configuration with caching.
 */
@Slf4j
public class ProducerConfigService implements ConfigService<ProducerConfigDTO> {

    private static final String PRODUCER_KEY_PREFIX = "producer:";
    private static final String DEFAULT_KEY = "default";
    private static final String PRODUCER_ID_PROP = "federator.producer.id";

    private final ManagementNodeDataHandler dataHandler;
    private final InMemoryConfigurationStore configStore;
    private final String configuredProducerId;

    /**
     * Constructs service with required dependencies.
     *
     * @param dataHandler handler for management node communication
     * @param configStore in-memory cache for configurations
     */
    public ProducerConfigService(
            final ManagementNodeDataHandler dataHandler, final InMemoryConfigurationStore configStore) {
        this.dataHandler = Objects.requireNonNull(dataHandler, "Data handler must not be null");
        this.configStore = Objects.requireNonNull(configStore, "Config store must not be null");
        this.configuredProducerId = PropertyUtil.getPropertyValue(PRODUCER_ID_PROP, null);
        log.info("Service initialized - Producer ID: {}", configuredProducerId != null ? configuredProducerId : "all");
    }

    // ----------------- ConfigService contract implementations -----------------
    @Override
    public InMemoryConfigurationStore getConfigStore() {
        return configStore;
    }

    @Override
    public String getKeyPrefix() {
        return PRODUCER_KEY_PREFIX;
    }

    @Override
    public String getConfiguredClientId() {
        return configuredProducerId;
    }

    @Override
    public Class<ProducerConfigDTO> getDtoClass() {
        return ProducerConfigDTO.class;
    }

    @Override
    public ProducerConfigDTO fetchConfiguration() {
        return dataHandler.getProducerData(configuredProducerId);
    }

    // Backwards-compatible convenience methods
    public ProducerConfigDTO getProducerConfiguration() {
        return getConfiguration();
    }
}
