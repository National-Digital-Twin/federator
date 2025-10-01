package uk.gov.dbt.ndtp.federator.service;

import java.util.Optional;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

/**
 * Generic configuration service contract with default cache management behavior.
 * Implementations must provide the specific DTO class, cache key prefix, configured id
 * and a method to fetch the configuration from the management node (fetchRemote).
 *
 * @param <T> type of configuration DTO
 */
public interface ConfigService<T> {

    /** Provide access to the shared in-memory configuration store. */
    InMemoryConfigurationStore getConfigStore();

    /** Prefix used when building cache keys (e.g. "producer:"). */
    String getKeyPrefix();

    /** The configured client id (may be null); used to build cache key. */
    String getConfiguredClientId();

    /** The DTO class type for cache retrieval casting. */
    Class<T> getDtoClass();

    /** Fetch the configuration from the remote management node. */
    T fetchConfiguration();

    /** Builds cache key based on prefix and configured id. */
    default String buildCacheKey() {
        final String DEFAULT_KEY = "default";
        String clientId = getConfiguredClientId();
        return getKeyPrefix() + (clientId != null ? clientId : DEFAULT_KEY);
    }

    /** Returns cached configuration if present. */
    default Optional<T> getCachedConfiguration() {
        return getConfigStore().get(buildCacheKey(), getDtoClass());
    }

    /** Returns configuration from cache or fetches and stores it when missing. */
    default T getConfiguration() {
        Optional<T> cached = getCachedConfiguration();
        if (cached.isPresent()) {
            return cached.get();
        }
        T cfg = fetchConfiguration();
        getConfigStore().store(buildCacheKey(), cfg);
        return cfg;
    }

    /** Refresh the configuration by clearing cache and fetching anew. */
    default void refreshConfigurations() {
        getConfigStore().clearCache();
        // populate cache
        T cfg = fetchConfiguration();
        getConfigStore().store(buildCacheKey(), cfg);
    }

    /** Clear the entire configuration cache. */
    default void clearCache() {
        getConfigStore().clearCache();
    }
}
