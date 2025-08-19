package uk.gov.dbt.ndtp.federator.storage;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.ConsumerConfiguration;
import uk.gov.dbt.ndtp.federator.model.ProducerConfiguration;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * In-memory cache implementation for storing configuration data.
 * Provides thread-safe operations with TTL support and size limits.
 */
@Slf4j
public class InMemoryConfigurationStore {

    private final Map<String, CacheEntry<ConsumerConfiguration>> consumerCache;
    private final Map<String, CacheEntry<ProducerConfiguration>> producerCache;

    private final long defaultTtlMillis;
    private final int maxCacheSize;

    // Statistics tracking
    private final AtomicLong consumerCacheHits = new AtomicLong(0);
    private final AtomicLong consumerCacheMisses = new AtomicLong(0);
    private final AtomicLong producerCacheHits = new AtomicLong(0);
    private final AtomicLong producerCacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);

    private volatile Instant lastEvictionTime;

    public InMemoryConfigurationStore(long defaultTtlMillis, int maxCacheSize) {
        this.consumerCache = new ConcurrentHashMap<>();
        this.producerCache = new ConcurrentHashMap<>();
        this.defaultTtlMillis = defaultTtlMillis;
        this.maxCacheSize = maxCacheSize;

        log.info("InMemoryConfigurationStore initialized with TTL: {}ms, max size: {}",
                defaultTtlMillis, maxCacheSize);
    }

    // ============== Consumer Configuration Methods ==============

    /**
     * Stores a consumer configuration in cache
     */
    public void storeConsumerConfiguration(ConsumerConfiguration configuration) {
        if (configuration == null || configuration.getClientId() == null) {
            log.warn("Attempted to store null or invalid consumer configuration");
            return;
        }

        enforceMaxCacheSize(consumerCache);

        CacheEntry<ConsumerConfiguration> entry = new CacheEntry<>(
                configuration,
                Instant.now().plusMillis(defaultTtlMillis)
        );

        consumerCache.put(configuration.getClientId(), entry);
        log.debug("Stored consumer configuration for client ID: {}", configuration.getClientId());
    }

    /**
     * Stores multiple consumer configurations
     */
    public void storeConsumerConfigurations(List<ConsumerConfiguration> configurations) {
        if (configurations == null) {
            return;
        }

        configurations.forEach(this::storeConsumerConfiguration);
        log.debug("Stored {} consumer configurations", configurations.size());
    }

    /**
     * Retrieves a consumer configuration by client ID
     */
    public Optional<ConsumerConfiguration> getConsumerConfiguration(String clientId) {
        if (clientId == null) {
            consumerCacheMisses.incrementAndGet();
            return Optional.empty();
        }

        CacheEntry<ConsumerConfiguration> entry = consumerCache.get(clientId);

        if (entry != null && !entry.isExpired()) {
            consumerCacheHits.incrementAndGet();
            log.debug("Cache hit for consumer configuration: {}", clientId);
            return Optional.of(entry.getData());
        }

        consumerCacheMisses.incrementAndGet();

        if (entry != null && entry.isExpired()) {
            consumerCache.remove(clientId);
            log.debug("Removed expired consumer configuration: {}", clientId);
        }

        return Optional.empty();
    }

    /**
     * Retrieves all valid consumer configurations
     */
    public List<ConsumerConfiguration> getAllConsumerConfigurations() {
        return consumerCache.values().stream()
                .filter(entry -> !entry.isExpired())
                .map(CacheEntry::getData)
                .toList();
    }

    /**
     * Clears all consumer configurations
     */
    public void clearConsumerConfigurations() {
        consumerCache.clear();
        log.info("Cleared all consumer configurations from cache");
    }

    // ============== Producer Configuration Methods ==============

    /**
     * Stores a producer configuration in cache
     */
    public void storeProducerConfiguration(ProducerConfiguration configuration) {
        if (configuration == null || configuration.getProducerId() == null) {
            log.warn("Attempted to store null or invalid producer configuration");
            return;
        }

        enforceMaxCacheSize(producerCache);

        CacheEntry<ProducerConfiguration> entry = new CacheEntry<>(
                configuration,
                Instant.now().plusMillis(defaultTtlMillis)
        );

        producerCache.put(configuration.getProducerId(), entry);
        log.debug("Stored producer configuration for producer ID: {}", configuration.getProducerId());
    }

    /**
     * Stores multiple producer configurations
     */
    public void storeProducerConfigurations(List<ProducerConfiguration> configurations) {
        if (configurations == null) {
            return;
        }

        configurations.forEach(this::storeProducerConfiguration);
        log.debug("Stored {} producer configurations", configurations.size());
    }

    /**
     * Retrieves a producer configuration by producer ID
     */
    public Optional<ProducerConfiguration> getProducerConfiguration(String producerId) {
        if (producerId == null) {
            producerCacheMisses.incrementAndGet();
            return Optional.empty();
        }

        CacheEntry<ProducerConfiguration> entry = producerCache.get(producerId);

        if (entry != null && !entry.isExpired()) {
            producerCacheHits.incrementAndGet();
            log.debug("Cache hit for producer configuration: {}", producerId);
            return Optional.of(entry.getData());
        }

        producerCacheMisses.incrementAndGet();

        if (entry != null && entry.isExpired()) {
            producerCache.remove(producerId);
            log.debug("Removed expired producer configuration: {}", producerId);
        }

        return Optional.empty();
    }

    /**
     * Retrieves all valid producer configurations
     */
    public List<ProducerConfiguration> getAllProducerConfigurations() {
        return producerCache.values().stream()
                .filter(entry -> !entry.isExpired())
                .map(CacheEntry::getData)
                .toList();
    }

    /**
     * Clears all producer configurations
     */
    public void clearProducerConfigurations() {
        producerCache.clear();
        log.info("Cleared all producer configurations from cache");
    }

    // ============== General Cache Methods ==============

    /**
     * Clears all configurations from cache
     */
    public void clearAll() {
        clearConsumerConfigurations();
        clearProducerConfigurations();
        log.info("Cleared all configurations from cache");
    }

    /**
     * Performs cleanup of expired entries
     */
    public CacheCleanupResult performCleanup() {
        log.debug("Starting cache cleanup");
        Instant cleanupStartTime = Instant.now();

        int expiredConsumers = removeExpiredEntries(consumerCache);
        int expiredProducers = removeExpiredEntries(producerCache);

        lastEvictionTime = Instant.now();

        CacheCleanupResult result = CacheCleanupResult.builder()
                .expiredConsumerConfigurations(expiredConsumers)
                .expiredProducerConfigurations(expiredProducers)
                .totalExpiredConfigurations(expiredConsumers + expiredProducers)
                .remainingConsumerConfigurations(consumerCache.size())
                .remainingProducerConfigurations(producerCache.size())
                .cleanupTime(Instant.now())
                .cleanupDurationMillis(Instant.now().toEpochMilli() - cleanupStartTime.toEpochMilli())
                .build();

        log.info("Cache cleanup completed: removed {} expired entries", result.getTotalExpiredConfigurations());

        return result;
    }

    /**
     * Gets cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        long totalSize = consumerCache.size() + producerCache.size();

        return CacheStatistics.builder()
                .totalCacheSize(totalSize)
                .consumerCacheSize(consumerCache.size())
                .producerCacheSize(producerCache.size())
                .consumerCacheHits(consumerCacheHits.get())
                .consumerCacheMisses(consumerCacheMisses.get())
                .producerCacheHits(producerCacheHits.get())
                .producerCacheMisses(producerCacheMisses.get())
                .cacheEvictions(cacheEvictions.get())
                .defaultTtlMillis(defaultTtlMillis)
                .maxCacheSize(maxCacheSize)
                .lastEvictionTime(lastEvictionTime)
                .averageLoadTime(calculateAverageLoadTime())
                .build();
    }

    // ============== Private Helper Methods ==============

    /**
     * Enforces maximum cache size by removing oldest entries
     */
    private <T> void enforceMaxCacheSize(Map<String, CacheEntry<T>> cache) {
        if (cache.size() >= maxCacheSize) {
            // Find and remove the oldest entry
            cache.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().getCreatedAt()))
                    .ifPresent(oldest -> {
                        cache.remove(oldest.getKey());
                        cacheEvictions.incrementAndGet();
                        lastEvictionTime = Instant.now();
                        log.debug("Evicted oldest cache entry: {}", oldest.getKey());
                    });
        }
    }

    /**
     * Removes expired entries from a cache
     */
    private <T> int removeExpiredEntries(Map<String, CacheEntry<T>> cache) {
        List<String> expiredKeys = cache.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .map(Map.Entry::getKey)
                .toList();

        expiredKeys.forEach(cache::remove);

        return expiredKeys.size();
    }

    /**
     * Calculates average load time (placeholder for actual implementation)
     */
    private long calculateAverageLoadTime() {
        // This would track actual load times in a production implementation
        return 100; // milliseconds
    }

    // ============== Inner Classes ==============

    /**
     * Cache entry wrapper with TTL support
     */
    private static class CacheEntry<T> {
        private final T data;
        private final Instant expiryTime;
        private final Instant createdAt;

        public CacheEntry(T data, Instant expiryTime) {
            this.data = data;
            this.expiryTime = expiryTime;
            this.createdAt = Instant.now();
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }

        public T getData() {
            return data;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * Cache cleanup result
     */
    @Builder
    @Data
    public static class CacheCleanupResult {
        private final int expiredConsumerConfigurations;
        private final int expiredProducerConfigurations;
        private final int totalExpiredConfigurations;
        private final int remainingConsumerConfigurations;
        private final int remainingProducerConfigurations;
        private final Instant cleanupTime;
        private final long cleanupDurationMillis;
    }

    /**
     * Cache statistics
     */
    @Builder
    @Data
    public static class CacheStatistics {
        private final long totalCacheSize;
        private final long consumerCacheSize;
        private final long producerCacheSize;
        private final long consumerCacheHits;
        private final long consumerCacheMisses;
        private final long producerCacheHits;
        private final long producerCacheMisses;
        private final long cacheEvictions;
        private final long defaultTtlMillis;
        private final int maxCacheSize;
        private final Instant lastEvictionTime;
        private final long averageLoadTime;

        public double getConsumerHitRate() {
            long total = consumerCacheHits + consumerCacheMisses;
            return total > 0 ? (double) consumerCacheHits / total * 100 : 0.0;
        }

        public double getProducerHitRate() {
            long total = producerCacheHits + producerCacheMisses;
            return total > 0 ? (double) producerCacheHits / total * 100 : 0.0;
        }

        public double getOverallHitRate() {
            long totalHits = consumerCacheHits + producerCacheHits;
            long totalMisses = consumerCacheMisses + producerCacheMisses;
            long total = totalHits + totalMisses;
            return total > 0 ? (double) totalHits / total * 100 : 0.0;
        }
    }
}