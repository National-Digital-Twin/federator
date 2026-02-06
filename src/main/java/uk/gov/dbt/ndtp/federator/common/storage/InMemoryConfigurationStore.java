// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Thread-safe in-memory cache for configuration data.
 * Provides temporary storage for configurations with TTL support.
 */
@Slf4j
@SuppressWarnings("java:S6548") // Singleton is intentional
public class InMemoryConfigurationStore {

    /**
     * Default TTL value in seconds.
     */
    private static final String DEFAULT_TTL_SECONDS = "3600";

    /**
     * Property key for cache TTL configuration.
     */
    private static final String CACHE_TTL_PROPERTY = "management.node.cache.ttl.seconds";

    private static final AtomicReference<InMemoryConfigurationStore> configurationStore = new AtomicReference<>();
    /**
     * Thread-safe cache storage.
     */
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    /**
     * Time-to-live for cache entries in seconds.
     */
    private final long ttlSeconds;

    private InMemoryConfigurationStore() {
        this.ttlSeconds = PropertyUtil.getPropertyLongValue(CACHE_TTL_PROPERTY, DEFAULT_TTL_SECONDS);
        log.info("Cache initialized with TTL: {} seconds", ttlSeconds);
    }

    public static InMemoryConfigurationStore getInstance() {
        return configurationStore.updateAndGet(current -> current != null ? current : new InMemoryConfigurationStore());
    }

    /**
     * Testing helper to reset the singleton instance so tests can reconfigure properties per test.
     * This forces the singleton to be recreated on next getInstance() call, picking up new PropertyUtil values.
     */
    public static void clearForTests() {
        configurationStore.set(null);
    }

    /**
     * Stores configuration in cache with specified key.
     *
     * @param key cache key, must not be null
     * @param config configuration object to store, must not be null
     * @param <T> type of configuration
     * @throws NullPointerException if key or config is null
     */
    public <T> void store(final String key, final T config) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(config, "Configuration must not be null");

        cache.put(key, new CacheEntry<>(config, Instant.now().plusSeconds(ttlSeconds)));
        log.debug("Stored configuration for key: {}", key);
    }

    /**
     * Retrieves configuration from cache by key.
     *
     * @param key cache key, must not be null
     * @param type expected type of configuration
     * @param <T> type of configuration
     * @return Optional containing config if present and not expired
     * @throws NullPointerException if key or type is null
     */
    public <T> Optional<T> get(final String key, final Class<T> type) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(type, "Type must not be null");

        final CacheEntry<?> entry = cache.get(key);

        if (entry == null) {
            return Optional.empty();
        }

        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(key);
            log.debug("Cache entry expired for key: {}", key);
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(type.cast(entry.value()));
        } catch (ClassCastException e) {
            log.error("Type mismatch for key: {}, expected: {}", key, type.getName(), e);
            cache.remove(key);
            return Optional.empty();
        }
    }

    /**
     * Clears all cached entries.
     */
    public void clearCache() {
        final int size = cache.size();
        cache.clear();
        log.info("Cache cleared, removed {} entries", size);
    }

    /**
     * Gets current cache size.
     *
     * @return number of entries in cache
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Immutable cache entry with expiration time.
     *
     * @param <T> type of cached value
     * @param value cached value
     * @param expiresAt expiration timestamp
     */
    private record CacheEntry<T>(T value, Instant expiresAt) {
        CacheEntry {
            Objects.requireNonNull(value, "Value must not be null");
            Objects.requireNonNull(expiresAt, "Expiration time must not be null");
        }
    }
}
