package uk.gov.dbt.ndtp.federator.storage;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory cache implementation for storing configuration data.
 * Provides thread-safe operations for caching producer and consumer configurations.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@Slf4j
public class InMemoryConfigurationStore {

    private static final long DEFAULT_TTL_SECONDS = 3600;
    private static final String PRODUCER_KEY_PREFIX = "producer:";
    private static final String CONSUMER_KEY_PREFIX = "consumer:";

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final long ttlSeconds;

    /**
     * Creates a new configuration store with default TTL of 1 hour.
     */
    public InMemoryConfigurationStore() {
        this(DEFAULT_TTL_SECONDS);
    }

    /**
     * Creates a new configuration store with specified TTL.
     *
     * @param ttlSeconds time-to-live for cache entries in seconds
     */
    public InMemoryConfigurationStore(final long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        log.info("Initialized configuration store with TTL: {} seconds", ttlSeconds);
    }

    /**
     * Stores producer configuration in cache.
     *
     * @param clientId the client identifier
     * @param config the producer configuration to store
     */
    public void storeProducerConfig(final String clientId, final ProducerConfigDTO config) {
        lock.writeLock().lock();
        try {
            final String key = PRODUCER_KEY_PREFIX + clientId;
            final CacheEntry<ProducerConfigDTO> entry = new CacheEntry<>(config, Instant.now().plusSeconds(ttlSeconds));
            cache.put(key, entry);
            log.debug("Stored producer config for client: {}", clientId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Stores consumer configuration in cache.
     *
     * @param clientId the client identifier
     * @param config the consumer configuration to store
     */
    public void storeConsumerConfig(final String clientId, final ConsumerConfigDTO config) {
        lock.writeLock().lock();
        try {
            final String key = CONSUMER_KEY_PREFIX + clientId;
            final CacheEntry<ConsumerConfigDTO> entry = new CacheEntry<>(config, Instant.now().plusSeconds(ttlSeconds));
            cache.put(key, entry);
            log.debug("Stored consumer config for client: {}", clientId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves producer configuration from cache.
     *
     * @param clientId the client identifier
     * @return the cached producer configuration, or null if not found or expired
     */
    public ProducerConfigDTO getProducerConfig(final String clientId) {
        lock.readLock().lock();
        try {
            final String key = PRODUCER_KEY_PREFIX + clientId;
            final CacheEntry<?> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                log.debug("Cache hit for producer config: {}", clientId);
                return (ProducerConfigDTO) entry.getValue();
            }
            log.debug("Cache miss for producer config: {}", clientId);
            evictIfExpired(key, entry);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retrieves consumer configuration from cache.
     *
     * @param clientId the client identifier
     * @return the cached consumer configuration, or null if not found or expired
     */
    public ConsumerConfigDTO getConsumerConfig(final String clientId) {
        lock.readLock().lock();
        try {
            final String key = CONSUMER_KEY_PREFIX + clientId;
            final CacheEntry<?> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                log.debug("Cache hit for consumer config: {}", clientId);
                return (ConsumerConfigDTO) entry.getValue();
            }
            log.debug("Cache miss for consumer config: {}", clientId);
            evictIfExpired(key, entry);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all cached configurations.
     */
    public void clearCache() {
        lock.writeLock().lock();
        try {
            cache.clear();
            log.info("Configuration cache cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Evicts expired entry from cache.
     *
     * @param key the cache key
     * @param entry the cache entry to check
     */
    private void evictIfExpired(final String key, final CacheEntry<?> entry) {
        if (entry != null && entry.isExpired()) {
            cache.remove(key);
            log.debug("Evicted expired entry: {}", key);
        }
    }

    /**
     * Internal cache entry wrapper with expiration support.
     *
     * @param <T> the type of cached value
     */
    private static class CacheEntry<T> {
        private final T value;
        private final Instant expiresAt;

        CacheEntry(final T value, final Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        T getValue() {
            return value;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}