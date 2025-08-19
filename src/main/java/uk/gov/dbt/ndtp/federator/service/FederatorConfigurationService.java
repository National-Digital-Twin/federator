package uk.gov.dbt.ndtp.federator.service;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.*;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.exceptions.ManagementNodeException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Enhanced service for managing federator configurations with advanced caching,
 * thread-safe operations, and automatic refresh capabilities.
 * This service works with Configuration models internally while the ManagementNodeDataHandler
 * handles DTO conversions.
 */
@Slf4j
public class FederatorConfigurationService {

    private final ManagementNodeDataHandler managementNodeDataHandler;
    private final JwtTokenService jwtTokenService;
    private final InMemoryConfigurationStore configurationStore;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService asyncExecutor;
    private final ReentrantReadWriteLock serviceLock = new ReentrantReadWriteLock(true);

    // Configuration parameters
    private final boolean cacheEnabled;
    private final boolean autoRefreshEnabled;
    private final long refreshIntervalMs;
    private final long cacheExpirationMs;
    private final boolean parallelRefreshEnabled;
    private final int maxRetryAttempts;
    private final Duration retryDelay;

    // Monitoring and statistics
    private final AtomicLong configurationRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong refreshAttempts = new AtomicLong(0);
    private final AtomicLong refreshSuccesses = new AtomicLong(0);
    private final AtomicLong refreshFailures = new AtomicLong(0);

    private volatile Instant lastSuccessfulRefresh;
    private volatile Instant lastRefreshAttempt;
    private volatile boolean isShutdown = false;

    // Circuit breaker state
    private volatile CircuitBreakerState circuitBreakerState = CircuitBreakerState.CLOSED;
    private volatile Instant circuitBreakerLastFailure;
    private final long circuitBreakerTimeout = 60000; // 1 minute

    public FederatorConfigurationService(Properties config) {
        this.managementNodeDataHandler = new ManagementNodeDataHandler(config);
        this.jwtTokenService = new JwtTokenService(config);

        // Parse configuration
        this.cacheEnabled = Boolean.parseBoolean(config.getProperty("federator.config.cache.enabled", "true"));
        this.autoRefreshEnabled = Boolean.parseBoolean(config.getProperty("federator.config.auto.refresh.enabled", "true"));
        this.refreshIntervalMs = Long.parseLong(config.getProperty("federator.config.refresh.interval", "3600000")); // 1 hour
        this.cacheExpirationMs = Long.parseLong(config.getProperty("federator.config.cache.expiration", "7200000")); // 2 hours
        this.parallelRefreshEnabled = Boolean.parseBoolean(config.getProperty("federator.config.parallel.refresh.enabled", "true"));
        this.maxRetryAttempts = Integer.parseInt(config.getProperty("federator.config.max.retry.attempts", "3"));
        this.retryDelay = Duration.ofSeconds(Long.parseLong(config.getProperty("federator.config.retry.delay.seconds", "5")));

        // Initialize storage
        this.configurationStore = new InMemoryConfigurationStore(cacheExpirationMs, 50000);

        // Initialize executors
        int threadPoolSize = Integer.parseInt(config.getProperty("federator.config.thread.pool.size", "5"));
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.asyncExecutor = Executors.newFixedThreadPool(threadPoolSize);

        initialize();
    }

    public FederatorConfigurationService(
            ManagementNodeDataHandler managementNodeDataHandler,
            JwtTokenService jwtTokenService,
            InMemoryConfigurationStore configurationStore,
            boolean cacheEnabled,
            boolean autoRefreshEnabled,
            long refreshIntervalMs,
            long cacheExpirationMs,
            boolean parallelRefreshEnabled) {

        this.managementNodeDataHandler = managementNodeDataHandler;
        this.jwtTokenService = jwtTokenService;
        this.configurationStore = configurationStore != null ? configurationStore :
                new InMemoryConfigurationStore(cacheExpirationMs, 50000);
        this.cacheEnabled = cacheEnabled;
        this.autoRefreshEnabled = autoRefreshEnabled;
        this.refreshIntervalMs = refreshIntervalMs;
        this.cacheExpirationMs = cacheExpirationMs;
        this.parallelRefreshEnabled = parallelRefreshEnabled;
        this.maxRetryAttempts = 3;
        this.retryDelay = Duration.ofSeconds(5);

        this.scheduler = Executors.newScheduledThreadPool(2);
        this.asyncExecutor = Executors.newFixedThreadPool(5);

        initialize();
    }

    private void initialize() {
        log.info("Initializing FederatorConfigurationService - cache: {}, autoRefresh: {}, refreshInterval: {}ms",
                cacheEnabled, autoRefreshEnabled, refreshIntervalMs);

        if (autoRefreshEnabled) {
            try {
                // Perform initial configuration load
                refreshConfigurationsSync();
                log.info("Initial configuration load completed successfully");

                // Schedule periodic refresh
                scheduler.scheduleAtFixedRate(
                        this::scheduledRefresh,
                        refreshIntervalMs,
                        refreshIntervalMs,
                        TimeUnit.MILLISECONDS
                );

                // Schedule periodic cache cleanup
                scheduler.scheduleAtFixedRate(
                        this::performCacheCleanup,
                        300000, // 5 minutes initial delay
                        300000, // 5 minutes interval
                        TimeUnit.MILLISECONDS
                );

            } catch (Exception e) {
                log.error("Failed to load initial configurations", e);
                circuitBreakerState = CircuitBreakerState.OPEN;
                circuitBreakerLastFailure = Instant.now();
            }
        }
    }

    /**
     * Retrieves all consumer configurations with caching and fallback mechanisms.
     * Returns Configuration models for internal use.
     */
    public List<ConsumerConfiguration> getConsumerConfigurations() throws ManagementNodeException {
        configurationRequests.incrementAndGet();
        log.debug("Retrieving consumer configurations");

        // Check cache first if enabled
        if (cacheEnabled) {
            List<ConsumerConfiguration> cachedConfigs = configurationStore.getAllConsumerConfigurations();
            if (!cachedConfigs.isEmpty()) {
                cacheHits.incrementAndGet();
                log.debug("Returning {} consumer configurations from cache", cachedConfigs.size());
                return cachedConfigs;
            }
            cacheMisses.incrementAndGet();
        }

        // Fetch from management node if cache miss
        if (circuitBreakerState == CircuitBreakerState.OPEN &&
                !isCircuitBreakerTimeoutExpired()) {
            throw new ManagementNodeException("Circuit breaker is open - management node calls are disabled");
        }

        try {
            String jwtToken = jwtTokenService.getCurrentToken();
            // ManagementNodeDataHandler now returns Configuration models directly
            List<ConsumerConfiguration> configurations = managementNodeDataHandler.getConsumerData(jwtToken);

            if (cacheEnabled) {
                configurationStore.storeConsumerConfigurations(configurations);
            }

            // Reset circuit breaker on success
            if (circuitBreakerState == CircuitBreakerState.OPEN) {
                circuitBreakerState = CircuitBreakerState.CLOSED;
                log.info("Circuit breaker reset to CLOSED state");
            }

            return configurations;

        } catch (Exception e) {
            handleServiceFailure(e);
            throw new ManagementNodeException("Failed to retrieve consumer configurations", e);
        }
    }

    /**
     * Retrieves all producer configurations with caching and fallback mechanisms.
     * Returns Configuration models for internal use.
     */
    public List<ProducerConfiguration> getProducerConfigurations() throws ManagementNodeException {
        configurationRequests.incrementAndGet();
        log.debug("Retrieving producer configurations");

        // Check cache first if enabled
        if (cacheEnabled) {
            List<ProducerConfiguration> cachedConfigs = configurationStore.getAllProducerConfigurations();
            if (!cachedConfigs.isEmpty()) {
                cacheHits.incrementAndGet();
                log.debug("Returning {} producer configurations from cache", cachedConfigs.size());
                return cachedConfigs;
            }
            cacheMisses.incrementAndGet();
        }

        // Fetch from management node if cache miss
        if (circuitBreakerState == CircuitBreakerState.OPEN &&
                !isCircuitBreakerTimeoutExpired()) {
            throw new ManagementNodeException("Circuit breaker is open - management node calls are disabled");
        }

        try {
            String jwtToken = jwtTokenService.getCurrentToken();
            // ManagementNodeDataHandler now returns Configuration models directly
            List<ProducerConfiguration> configurations = managementNodeDataHandler.getProducerData(jwtToken);

            if (cacheEnabled) {
                configurationStore.storeProducerConfigurations(configurations);
            }

            // Reset circuit breaker on success
            if (circuitBreakerState == CircuitBreakerState.OPEN) {
                circuitBreakerState = CircuitBreakerState.CLOSED;
                log.info("Circuit breaker reset to CLOSED state");
            }

            return configurations;

        } catch (Exception e) {
            handleServiceFailure(e);
            throw new ManagementNodeException("Failed to retrieve producer configurations", e);
        }
    }

    /**
     * Retrieves a specific consumer configuration by client ID with enhanced caching.
     * Returns Configuration model for internal use.
     */
    public ConsumerConfiguration getConsumerConfiguration(String clientId) throws ManagementNodeException {
        configurationRequests.incrementAndGet();
        log.debug("Retrieving consumer configuration for client ID: {}", clientId);

        // Check cache first if enabled
        if (cacheEnabled) {
            Optional<ConsumerConfiguration> cached = configurationStore.getConsumerConfiguration(clientId);
            if (cached.isPresent()) {
                cacheHits.incrementAndGet();
                log.debug("Returning cached consumer configuration for client ID: {}", clientId);
                return cached.get();
            }
            cacheMisses.incrementAndGet();
        }

        // Circuit breaker check
        if (circuitBreakerState == CircuitBreakerState.OPEN &&
                !isCircuitBreakerTimeoutExpired()) {
            throw new ManagementNodeException("Circuit breaker is open - management node calls are disabled");
        }

        // Fetch from management node
        try {
            String jwtToken = jwtTokenService.getCurrentToken();
            // Use the method that returns Configuration model
            ConsumerConfiguration configuration = managementNodeDataHandler.getConsumerConfigurationByClientId(jwtToken, clientId);

            // Update cache
            if (cacheEnabled && configuration != null) {
                configurationStore.storeConsumerConfiguration(configuration);
            }

            // Reset circuit breaker on success
            if (circuitBreakerState == CircuitBreakerState.OPEN) {
                circuitBreakerState = CircuitBreakerState.CLOSED;
                log.info("Circuit breaker reset to CLOSED state");
            }

            return configuration;

        } catch (Exception e) {
            handleServiceFailure(e);
            throw new ManagementNodeException("Failed to retrieve consumer configuration for client ID: " + clientId, e);
        }
    }

    /**
     * Retrieves a specific consumer DTO by client ID.
     * Returns DTO for API responses.
     */
    public ConsumerDTO getConsumerDTO(String clientId) throws ManagementNodeException {
        String jwtToken = jwtTokenService.getCurrentToken();
        return managementNodeDataHandler.getConsumerDataByClientId(jwtToken, clientId);
    }

    /**
     * Retrieves a specific producer configuration by producer ID with enhanced caching.
     * Returns Configuration model for internal use.
     */
    public ProducerConfiguration getProducerConfiguration(String producerId) throws ManagementNodeException {
        configurationRequests.incrementAndGet();
        log.debug("Retrieving producer configuration for producer ID: {}", producerId);

        // Check cache first if enabled
        if (cacheEnabled) {
            Optional<ProducerConfiguration> cached = configurationStore.getProducerConfiguration(producerId);
            if (cached.isPresent()) {
                cacheHits.incrementAndGet();
                log.debug("Returning cached producer configuration for producer ID: {}", producerId);
                return cached.get();
            }
            cacheMisses.incrementAndGet();
        }

        // Circuit breaker check
        if (circuitBreakerState == CircuitBreakerState.OPEN &&
                !isCircuitBreakerTimeoutExpired()) {
            throw new ManagementNodeException("Circuit breaker is open - management node calls are disabled");
        }

        // Fetch from management node
        try {
            String jwtToken = jwtTokenService.getCurrentToken();
            // Use the method that returns Configuration model
            ProducerConfiguration configuration = managementNodeDataHandler.getProducerConfigurationByProducerId(jwtToken, producerId);

            // Update cache
            if (cacheEnabled && configuration != null) {
                configurationStore.storeProducerConfiguration(configuration);
            }

            // Reset circuit breaker on success
            if (circuitBreakerState == CircuitBreakerState.OPEN) {
                circuitBreakerState = CircuitBreakerState.CLOSED;
                log.info("Circuit breaker reset to CLOSED state");
            }

            return configuration;

        } catch (Exception e) {
            handleServiceFailure(e);
            throw new ManagementNodeException("Failed to retrieve producer configuration for producer ID: " + producerId, e);
        }
    }

    /**
     * Retrieves a specific producer DTO by producer ID.
     * Returns DTO for API responses.
     */
    public ProducerDTO getProducerDTO(String producerId) throws ManagementNodeException {
        String jwtToken = jwtTokenService.getCurrentToken();
        return managementNodeDataHandler.getProducerDataByProducerId(jwtToken, producerId);
    }

    /**
     * Retrieves producer configuration response with client ID.
     */
    public ManagementNodeDataHandler.ProducerConfigurationResponse getProducerConfigurationResponse(String producerId)
            throws ManagementNodeException {
        String jwtToken = jwtTokenService.getCurrentToken();
        return managementNodeDataHandler.getProducerConfigurationResponse(jwtToken, producerId);
    }

    /**
     * Validates if a client has access to a specific topic with enhanced error handling.
     */
    public boolean hasTopicAccess(String clientId, String topicName) {
        try {
            ConsumerConfiguration config = getConsumerConfiguration(clientId);
            if (config == null || config.getTopics() == null) {
                return false;
            }

            return config.getTopics().stream()
                    .anyMatch(topic -> topicName.equals(topic.getName()));

        } catch (ManagementNodeException e) {
            log.error("Failed to check topic access for client {} and topic {}", clientId, topicName, e);
            return false;
        }
    }

    /**
     * Checks if a client's API configuration is valid and not revoked.
     */
    public boolean isClientApiValid(String clientId) {
        try {
            ConsumerConfiguration config = getConsumerConfiguration(clientId);
            if (config == null || config.getApi() == null) {
                return false;
            }

            return !config.getApi().isRevoked();

        } catch (ManagementNodeException e) {
            log.error("Failed to check API validity for client {}", clientId, e);
            return false;
        }
    }

    /**
     * Gets the filter class name for a specific client.
     */
    public String getClientFilterClassName(String clientId) {
        try {
            ConsumerConfiguration config = getConsumerConfiguration(clientId);
            return config != null ? config.getFilterClassname() : null;

        } catch (ManagementNodeException e) {
            log.error("Failed to get filter class name for client {}", clientId, e);
            return null;
        }
    }

    /**
     * Gets Kafka configuration for a specific client.
     */
    public KafkaConfiguration getClientKafkaConfiguration(String clientId) {
        try {
            ConsumerConfiguration config = getConsumerConfiguration(clientId);
            return config != null ? config.getKafka() : null;

        } catch (ManagementNodeException e) {
            log.error("Failed to get Kafka configuration for client {}", clientId, e);
            return null;
        }
    }

    /**
     * Gets Security configuration for a specific client.
     */
    public SecurityConfiguration getClientSecurityConfiguration(String clientId) {
        try {
            ConsumerConfiguration config = getConsumerConfiguration(clientId);
            return config != null ? config.getSecurity() : null;

        } catch (ManagementNodeException e) {
            log.error("Failed to get Security configuration for client {}", clientId, e);
            return null;
        }
    }

    /**
     * Asynchronously refreshes all configurations.
     */
    public CompletableFuture<RefreshResult> refreshConfigurationsAsync() {
        return CompletableFuture.supplyAsync(this::refreshConfigurationsSync, asyncExecutor);
    }

    /**
     * Synchronously refreshes all configurations with retry logic.
     */
    public RefreshResult refreshConfigurationsSync() {
        serviceLock.writeLock().lock();
        try {
            return performRefreshWithRetry();
        } finally {
            serviceLock.writeLock().unlock();
        }
    }

    /**
     * Performs configuration refresh with retry mechanism.
     */
    private RefreshResult performRefreshWithRetry() {
        refreshAttempts.incrementAndGet();
        lastRefreshAttempt = Instant.now();

        log.info("Starting configuration refresh (attempt {}/{})", 1, maxRetryAttempts);

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                RefreshResult result = performSingleRefresh();

                refreshSuccesses.incrementAndGet();
                lastSuccessfulRefresh = Instant.now();

                // Reset circuit breaker on success
                if (circuitBreakerState == CircuitBreakerState.OPEN) {
                    circuitBreakerState = CircuitBreakerState.CLOSED;
                    log.info("Circuit breaker reset to CLOSED state after successful refresh");
                }

                return result;

            } catch (Exception e) {
                lastException = e;
                log.warn("Refresh attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(retryDelay.toMillis() * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread interrupted during configuration refresh", ie);
                    }
                }
            }
        }

        refreshFailures.incrementAndGet();
        handleServiceFailure(lastException);

        throw new RuntimeException("Configuration refresh failed after " + maxRetryAttempts + " attempts", lastException);
    }

    /**
     * Performs a single refresh operation.
     */
    private RefreshResult performSingleRefresh() throws ManagementNodeException {
        Instant startTime = Instant.now();

        if (parallelRefreshEnabled) {
            return performParallelRefresh(startTime);
        } else {
            return performSequentialRefresh(startTime);
        }
    }

    /**
     * Performs parallel refresh of consumer and producer configurations.
     */
    private RefreshResult performParallelRefresh(Instant startTime) {
        CompletableFuture<List<ConsumerConfiguration>> consumerFuture =
                managementNodeDataHandler.getConsumerDataAsync(jwtTokenService.getCurrentToken());

        CompletableFuture<List<ProducerConfiguration>> producerFuture =
                managementNodeDataHandler.getProducerDataAsync(jwtTokenService.getCurrentToken());

        try {
            CompletableFuture.allOf(consumerFuture, producerFuture).get(30, TimeUnit.SECONDS);

            List<ConsumerConfiguration> consumers = consumerFuture.get();
            List<ProducerConfiguration> producers = producerFuture.get();

            if (cacheEnabled) {
                configurationStore.storeConsumerConfigurations(consumers);
                configurationStore.storeProducerConfigurations(producers);
            }

            Duration refreshDuration = Duration.between(startTime, Instant.now());

            RefreshResult result = RefreshResult.builder()
                    .success(true)
                    .consumerCount(consumers.size())
                    .producerCount(producers.size())
                    .refreshDuration(refreshDuration)
                    .refreshTime(Instant.now())
                    .refreshType(RefreshType.PARALLEL)
                    .build();

            log.info("Parallel refresh completed: {} consumers, {} producers in {}ms",
                    consumers.size(), producers.size(), refreshDuration.toMillis());

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Parallel refresh failed", e);
        }
    }

    /**
     * Performs sequential refresh of consumer and producer configurations.
     */
    private RefreshResult performSequentialRefresh(Instant startTime) throws ManagementNodeException {
        String jwtToken = jwtTokenService.getCurrentToken();

        List<ConsumerConfiguration> consumers = managementNodeDataHandler.getConsumerData(jwtToken);
        List<ProducerConfiguration> producers = managementNodeDataHandler.getProducerData(jwtToken);

        if (cacheEnabled) {
            configurationStore.storeConsumerConfigurations(consumers);
            configurationStore.storeProducerConfigurations(producers);
        }

        Duration refreshDuration = Duration.between(startTime, Instant.now());

        RefreshResult result = RefreshResult.builder()
                .success(true)
                .consumerCount(consumers.size())
                .producerCount(producers.size())
                .refreshDuration(refreshDuration)
                .refreshTime(Instant.now())
                .refreshType(RefreshType.SEQUENTIAL)
                .build();

        log.info("Sequential refresh completed: {} consumers, {} producers in {}ms",
                consumers.size(), producers.size(), refreshDuration.toMillis());

        return result;
    }

    /**
     * Scheduled method to automatically refresh configurations.
     */
    private void scheduledRefresh() {
        if (!autoRefreshEnabled || isShutdown) {
            return;
        }

        log.debug("Performing scheduled configuration refresh");
        try {
            refreshConfigurationsSync();
        } catch (Exception e) {
            log.error("Scheduled configuration refresh failed", e);
        }
    }

    /**
     * Performs periodic cache cleanup.
     */
    private void performCacheCleanup() {
        if (!cacheEnabled || isShutdown) {
            return;
        }

        try {
            InMemoryConfigurationStore.CacheCleanupResult result = configurationStore.performCleanup();
            if (result.getTotalExpiredConfigurations() > 0) {
                log.info("Cache cleanup removed {} expired configurations", result.getTotalExpiredConfigurations());
            }
        } catch (Exception e) {
            log.error("Cache cleanup failed", e);
        }
    }

    /**
     * Handles service failures and manages circuit breaker state.
     */
    private void handleServiceFailure(Exception e) {
        circuitBreakerLastFailure = Instant.now();

        if (circuitBreakerState == CircuitBreakerState.CLOSED) {
            circuitBreakerState = CircuitBreakerState.OPEN;
            log.warn("Circuit breaker opened due to failure: {}", e.getMessage());
        }
    }

    /**
     * Checks if circuit breaker timeout has expired.
     */
    private boolean isCircuitBreakerTimeoutExpired() {
        return circuitBreakerLastFailure == null ||
                Instant.now().minusMillis(circuitBreakerTimeout).isAfter(circuitBreakerLastFailure);
    }

    /**
     * Returns comprehensive service statistics for monitoring.
     */
    public FederatorServiceStatistics getServiceStatistics() {
        InMemoryConfigurationStore.CacheStatistics cacheStats =
                cacheEnabled ? configurationStore.getCacheStatistics() : null;

        ManagementNodeDataHandler.ManagementNodeStatistics mgmtStats =
                managementNodeDataHandler.getStatistics();

        JwtTokenService.JwtTokenInfo tokenInfo = jwtTokenService.getTokenInfo();

        return FederatorServiceStatistics.builder()
                .configurationRequests(configurationRequests.get())
                .cacheHits(cacheHits.get())
                .cacheMisses(cacheMisses.get())
                .refreshAttempts(refreshAttempts.get())
                .refreshSuccesses(refreshSuccesses.get())
                .refreshFailures(refreshFailures.get())
                .lastSuccessfulRefresh(lastSuccessfulRefresh)
                .lastRefreshAttempt(lastRefreshAttempt)
                .circuitBreakerState(circuitBreakerState)
                .circuitBreakerLastFailure(circuitBreakerLastFailure)
                .cacheStatistics(cacheStats)
                .managementNodeStatistics(mgmtStats)
                .tokenInfo(tokenInfo)
                .cacheEnabled(cacheEnabled)
                .autoRefreshEnabled(autoRefreshEnabled)
                .refreshIntervalMs(refreshIntervalMs)
                .parallelRefreshEnabled(parallelRefreshEnabled)
                .build();
    }

    /**
     * Health check method that validates all components.
     */
    public HealthCheckResult performHealthCheck() {
        boolean isHealthy = true;
        Map<String, String> healthDetails = new ConcurrentHashMap<>();

        // Check management node connectivity
        boolean mgmtNodeHealthy = managementNodeDataHandler.isHealthy();
        healthDetails.put("managementNode", mgmtNodeHealthy ? "HEALTHY" : "UNHEALTHY");
        isHealthy = isHealthy && mgmtNodeHealthy;

        // Check JWT token validity
        JwtTokenService.JwtTokenInfo tokenInfo = jwtTokenService.getTokenInfo();
        boolean tokenValid = tokenInfo.isValid();
        healthDetails.put("jwtToken", tokenValid ? "VALID" : "INVALID");
        isHealthy = isHealthy && tokenValid;

        // Check circuit breaker state
        healthDetails.put("circuitBreaker", circuitBreakerState.name());
        isHealthy = isHealthy && (circuitBreakerState != CircuitBreakerState.OPEN);

        // Check cache status if enabled
        if (cacheEnabled) {
            InMemoryConfigurationStore.CacheStatistics cacheStats = configurationStore.getCacheStatistics();
            boolean cacheHealthy = cacheStats.getTotalCacheSize() >= 0; // Basic sanity check
            healthDetails.put("cache", cacheHealthy ? "HEALTHY" : "UNHEALTHY");
            isHealthy = isHealthy && cacheHealthy;
        }

        return HealthCheckResult.builder()
                .healthy(isHealthy)
                .checkTime(Instant.now())
                .details(healthDetails)
                .build();
    }

    /**
     * Closes resources and shuts down services.
     */
    public void close() {
        isShutdown = true;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (asyncExecutor != null && !asyncExecutor.isShutdown()) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (managementNodeDataHandler != null) {
            managementNodeDataHandler.close();
        }

        if (jwtTokenService != null) {
            jwtTokenService.close();
        }

        log.info("FederatorConfigurationService closed");
    }

    // Enums and Data Classes

    public enum CircuitBreakerState {
        CLOSED, OPEN, HALF_OPEN
    }

    public enum RefreshType {
        SEQUENTIAL, PARALLEL
    }

    @lombok.Builder
    @lombok.Data
    public static class RefreshResult {
        private final boolean success;
        private final int consumerCount;
        private final int producerCount;
        private final Duration refreshDuration;
        private final Instant refreshTime;
        private final RefreshType refreshType;
        private final String errorMessage;
    }

    @lombok.Builder
    @lombok.Data
    public static class FederatorServiceStatistics {
        private final long configurationRequests;
        private final long cacheHits;
        private final long cacheMisses;
        private final long refreshAttempts;
        private final long refreshSuccesses;
        private final long refreshFailures;
        private final Instant lastSuccessfulRefresh;
        private final Instant lastRefreshAttempt;
        private final CircuitBreakerState circuitBreakerState;
        private final Instant circuitBreakerLastFailure;
        private final InMemoryConfigurationStore.CacheStatistics cacheStatistics;
        private final ManagementNodeDataHandler.ManagementNodeStatistics managementNodeStatistics;
        private final JwtTokenService.JwtTokenInfo tokenInfo;
        private final boolean cacheEnabled;
        private final boolean autoRefreshEnabled;
        private final long refreshIntervalMs;
        private final boolean parallelRefreshEnabled;

        public double getCacheHitRate() {
            long totalRequests = cacheHits + cacheMisses;
            return totalRequests > 0 ? (double) cacheHits / totalRequests * 100 : 0.0;
        }

        public double getRefreshSuccessRate() {
            return refreshAttempts > 0 ? (double) refreshSuccesses / refreshAttempts * 100 : 0.0;
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class HealthCheckResult {
        private final boolean healthy;
        private final Instant checkTime;
        private final Map<String, String> details;
    }
}