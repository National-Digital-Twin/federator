// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.storage;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for configuration data.
 */
@Slf4j
public class InMemoryConfigurationStore {

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    /**
     * Creates store with TTL from properties.
     */
    public InMemoryConfigurationStore() {
        this.ttlSeconds = PropertyUtil.getPropertyLongValue("cache.ttl.seconds", "3600");
        log.info("Cache initialized with TTL: {} seconds", ttlSeconds);
    }

    /**
     * Stores producer configuration.
     *
     * @param clientId client identifier
     * @param config configuration to store
     */
    public void storeProducerConfig(final String clientId, final ProducerConfigDTO config) {
        store("producer:" + clientId, config);
    }

    /**
     * Stores consumer configuration.
     *
     * @param clientId client identifier
     * @param config configuration to store
     */
    public void storeConsumerConfig(final String clientId, final ConsumerConfigDTO config) {
        store("consumer:" + clientId, config);
    }

    /**
     * Gets producer configuration.
     *
     * @param clientId client identifier
     * @return cached config or null
     */
    public ProducerConfigDTO getProducerConfig(final String clientId) {
        return get("producer:" + clientId, ProducerConfigDTO.class);
    }

    /**
     * Gets consumer configuration.
     *
     * @param clientId client identifier
     * @return cached config or null
     */
    public ConsumerConfigDTO getConsumerConfig(final String clientId) {
        return get("consumer:" + clientId, ConsumerConfigDTO.class);
    }

    /**
     * Clears all cached entries.
     */
    public void clearCache() {
        cache.clear();
        log.info("Cache cleared");
    }

    /**
     * Gets cache size.
     *
     * @return number of entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Generic store method.
     */
    private void store(final String key, final Object value) {
        cache.put(key, new CacheEntry<>(value, Instant.now().plusSeconds(ttlSeconds)));
        log.debug("Stored: {}", key);
    }

    /**
     * Generic get method with expiry check.
     */
    private <T> T get(final String key, final Class<T> type) {
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && Instant.now().isBefore(entry.expiresAt)) {
            // Use type.cast() for type-safe casting
            return type.cast(entry.value);
        }
        if (entry != null) {
            cache.remove(key);
        }
        return null;
    }

    /**
     * Cache entry with expiration.
     */
    private record CacheEntry<T>(T value, Instant expiresAt) {}
}