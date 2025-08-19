package uk.gov.dbt.ndtp.federator.service;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Enhanced service for managing JWT tokens used for management node authentication.
 * Provides automatic token refresh, file watching, and comprehensive token validation.
 */
@Slf4j
public class JwtTokenService {

    private final ReentrantReadWriteLock tokenLock = new ReentrantReadWriteLock(true); // Fair lock
    private volatile String currentToken;
    private volatile Instant tokenExpiry;
    private volatile Instant lastRefresh;

    private final String configuredToken;
    private final String tokenFile;
    private final long refreshBufferSeconds;
    private final boolean autoRefreshEnabled;
    private final boolean fileWatchingEnabled;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService fileWatchExecutor;
    private final ObjectMapper objectMapper;

    private final AtomicLong refreshCount = new AtomicLong(0);
    private final AtomicLong validationCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    private volatile WatchService watchService;
    private volatile boolean isShutdown = false;

    public JwtTokenService(Properties config) {
        this.configuredToken = config.getProperty("management.node.jwt.token", "");
        this.tokenFile = config.getProperty("management.node.jwt.token.file", "");
        this.refreshBufferSeconds = Long.parseLong(config.getProperty("management.node.jwt.token.refresh.buffer", "300"));
        this.autoRefreshEnabled = Boolean.parseBoolean(config.getProperty("management.node.jwt.token.auto.refresh.enabled", "true"));
        this.fileWatchingEnabled = Boolean.parseBoolean(config.getProperty("management.node.jwt.token.file.watching.enabled", "true"));

        this.scheduler = Executors.newScheduledThreadPool(1);
        this.fileWatchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jwt-file-watcher");
            t.setDaemon(true);
            return t;
        });
        this.objectMapper = new ObjectMapper();

        initialize();

        log.info("JwtTokenService initialized - autoRefresh: {}, fileWatching: {}, refreshBuffer: {}s",
                autoRefreshEnabled, fileWatchingEnabled, refreshBufferSeconds);
    }

    public JwtTokenService(String configuredToken, String tokenFile, long refreshBufferSeconds,
                           boolean autoRefreshEnabled, boolean fileWatchingEnabled) {
        this.configuredToken = configuredToken != null ? configuredToken : "";
        this.tokenFile = tokenFile != null ? tokenFile : "";
        this.refreshBufferSeconds = refreshBufferSeconds;
        this.autoRefreshEnabled = autoRefreshEnabled;
        this.fileWatchingEnabled = fileWatchingEnabled;

        this.scheduler = Executors.newScheduledThreadPool(1);
        this.fileWatchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jwt-file-watcher");
            t.setDaemon(true);
            return t;
        });
        this.objectMapper = new ObjectMapper();

        initialize();
    }

    private void initialize() {
        try {
            // Initial token load
            refreshToken();

            // Setup auto-refresh if enabled
            if (autoRefreshEnabled) {
                setupAutoRefresh();
            }

            // Setup file watching if enabled and token file is specified
            if (fileWatchingEnabled && !tokenFile.trim().isEmpty()) {
                setupFileWatching();
            }

        } catch (Exception e) {
            log.error("Failed to initialize JwtTokenService", e);
        }
    }

    /**
     * Gets the current valid JWT token with enhanced validation.
     */
    public String getCurrentToken() {
        validationCount.incrementAndGet();

        tokenLock.readLock().lock();
        try {
            if (isTokenValid()) {
                return currentToken;
            }
        } finally {
            tokenLock.readLock().unlock();
        }

        // Token is invalid, try to refresh
        tokenLock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            if (isTokenValid()) {
                return currentToken;
            }

            refreshToken();

            if (currentToken == null || currentToken.trim().isEmpty()) {
                errorCount.incrementAndGet();
                throw new IllegalStateException("No valid JWT token available for management node authentication");
            }

            return currentToken;
        } finally {
            tokenLock.writeLock().unlock();
        }
    }

    /**
     * Manually sets a new JWT token with validation.
     */
    public void setToken(String token, Instant expiryTime) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        tokenLock.writeLock().lock();
        try {
            // Validate token format
            if (!isValidJwtFormat(token)) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }

            this.currentToken = token.trim();
            this.tokenExpiry = expiryTime;
            this.lastRefresh = Instant.now();
            refreshCount.incrementAndGet();

            log.info("JWT token manually set, expires at: {}", expiryTime);
        } finally {
            tokenLock.writeLock().unlock();
        }
    }

    /**
     * Forces a token refresh from the configured source with enhanced error handling.
     */
    public void refreshToken() {
        log.debug("Refreshing JWT token");

        try {
            String newToken = loadTokenFromSources();

            if (newToken != null && !newToken.trim().isEmpty()) {
                tokenLock.writeLock().lock();
                try {
                    if (!isValidJwtFormat(newToken)) {
                        log.error("Loaded token has invalid JWT format");
                        errorCount.incrementAndGet();
                        return;
                    }

                    this.currentToken = newToken.trim();
                    this.tokenExpiry = extractTokenExpiry(currentToken);
                    this.lastRefresh = Instant.now();
                    refreshCount.incrementAndGet();

                    log.info("JWT token refreshed successfully, expires at: {}", tokenExpiry);
                } finally {
                    tokenLock.writeLock().unlock();
                }
            } else {
                log.warn("No JWT token source configured or available");
                errorCount.incrementAndGet();
            }
        } catch (Exception e) {
            log.error("Failed to refresh JWT token", e);
            errorCount.incrementAndGet();
        }
    }

    /**
     * Loads token from configured sources with priority order.
     */
    private String loadTokenFromSources() {
        // Priority 1: Configured token (highest priority)
        if (!configuredToken.trim().isEmpty()) {
            log.debug("Using configured JWT token");
            return configuredToken.trim();
        }

        // Priority 2: Token file
        if (!tokenFile.trim().isEmpty()) {
            try {
                String fileToken = readTokenFromFile(tokenFile.trim());
                if (fileToken != null && !fileToken.trim().isEmpty()) {
                    log.debug("Loaded JWT token from file: {}", tokenFile);
                    return fileToken.trim();
                }
            } catch (Exception e) {
                log.error("Failed to read JWT token from file: {}", tokenFile, e);
            }
        }

        // Priority 3: Environment variable (fallback)
        String envToken = System.getenv("MANAGEMENT_NODE_JWT_TOKEN");
        if (envToken != null && !envToken.trim().isEmpty()) {
            log.debug("Using JWT token from environment variable");
            return envToken.trim();
        }

        return null;
    }

    /**
     * Checks if the current token is valid and not expired.
     */
    private boolean isTokenValid() {
        if (currentToken == null || currentToken.trim().isEmpty()) {
            return false;
        }

        if (!isValidJwtFormat(currentToken)) {
            log.warn("Current token has invalid JWT format");
            return false;
        }

        if (tokenExpiry == null) {
            // If we don't know the expiry, check if token is well-formed
            return true;
        }

        // Check if token expires within the buffer time
        Instant now = Instant.now();
        Instant refreshTime = tokenExpiry.minusSeconds(refreshBufferSeconds);

        return now.isBefore(refreshTime);
    }

    /**
     * Validates JWT token format (header.payload.signature).
     */
    private boolean isValidJwtFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        // Validate each part is valid Base64
        try {
            for (String part : parts) {
                Base64.getUrlDecoder().decode(part);
            }
            return true;
        } catch (IllegalArgumentException e) {
            log.debug("Invalid Base64 in JWT token", e);
            return false;
        }
    }

    /**
     * Extracts the expiry time from a JWT token with enhanced parsing.
     */
    private Instant extractTokenExpiry(String token) {
        try {
            // Split JWT into parts
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid JWT token format for expiry extraction");
                return null;
            }

            // Decode payload (second part)
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(payloadBytes);

            // Parse JSON to extract 'exp' claim
            JsonNode payloadNode = objectMapper.readTree(payload);
            JsonNode expNode = payloadNode.get("exp");

            if (expNode != null && expNode.isNumber()) {
                long expTimestamp = expNode.asLong();
                Instant expiry = Instant.ofEpochSecond(expTimestamp);
                log.debug("Extracted token expiry: {}", expiry);
                return expiry;
            }

        } catch (Exception e) {
            log.debug("Failed to extract expiry from JWT token", e);
        }

        return null;
    }

    /**
     * Reads JWT token from a file with enhanced error handling.
     */
    private String readTokenFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("Token file does not exist: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new IOException("Token file is not readable: " + filePath);
        }

        String content = Files.readString(path).trim();

        if (content.isEmpty()) {
            throw new IOException("Token file is empty: " + filePath);
        }

        return content;
    }

    /**
     * Sets up automatic token refresh based on expiry.
     */
    private void setupAutoRefresh() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isShutdown) {
                    tokenLock.readLock().lock();
                    try {
                        if (!isTokenValid()) {
                            log.debug("Token needs refresh, triggering automatic refresh");
                            refreshToken();
                        }
                    } finally {
                        tokenLock.readLock().unlock();
                    }
                }
            } catch (Exception e) {
                log.error("Auto-refresh failed", e);
                errorCount.incrementAndGet();
            }
        }, 60, 60, TimeUnit.SECONDS); // Check every minute

        log.info("Auto-refresh scheduled to check every 60 seconds");
    }

    /**
     * Sets up file watching for automatic token reload when file changes.
     */
    private void setupFileWatching() {
        if (tokenFile.trim().isEmpty()) {
            return;
        }

        fileWatchExecutor.submit(() -> {
            try {
                Path filePath = Paths.get(tokenFile);
                Path parentDir = filePath.getParent();

                if (parentDir == null || !Files.exists(parentDir)) {
                    log.warn("Token file parent directory does not exist: {}", parentDir);
                    return;
                }

                watchService = parentDir.getFileSystem().newWatchService();
                parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                log.info("Started watching token file: {}", tokenFile);

                while (!isShutdown) {
                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path modifiedFile = (Path) event.context();

                            if (filePath.getFileName().equals(modifiedFile)) {
                                log.info("Token file modified, refreshing token");
                                Thread.sleep(100); // Brief delay to ensure file write is complete
                                refreshToken();
                            }
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        log.warn("Watch key no longer valid, stopping file watching");
                        break;
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("File watching interrupted");
            } catch (Exception e) {
                log.error("File watching failed", e);
            }
        });
    }

    /**
     * Validates token against the management node (if health endpoint is available).
     */
    public boolean validateTokenRemotely(String managementNodeBaseUrl) {
        // This would make a test call to the management node to validate the token
        // Implementation depends on available endpoints
        log.debug("Remote token validation not implemented yet");
        return true;
    }

    /**
     * Gets comprehensive token information for monitoring purposes.
     */
    public JwtTokenInfo getTokenInfo() {
        tokenLock.readLock().lock();
        try {
            return JwtTokenInfo.builder()
                    .hasToken(currentToken != null && !currentToken.trim().isEmpty())
                    .tokenExpiry(tokenExpiry)
                    .lastRefresh(lastRefresh)
                    .isValid(isTokenValid())
                    .refreshCount(refreshCount.get())
                    .validationCount(validationCount.get())
                    .errorCount(errorCount.get())
                    .autoRefreshEnabled(autoRefreshEnabled)
                    .fileWatchingEnabled(fileWatchingEnabled)
                    .refreshBufferSeconds(refreshBufferSeconds)
                    .build();
        } finally {
            tokenLock.readLock().unlock();
        }
    }

    /**
     * Shuts down the service and releases resources.
     */
    public void close() {
        isShutdown = true;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (fileWatchExecutor != null && !fileWatchExecutor.isShutdown()) {
            fileWatchExecutor.shutdown();
            try {
                if (!fileWatchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    fileWatchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                fileWatchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            }
        }

        log.info("JwtTokenService shut down");
    }

    /**
     * Data class for token information
     */
    @lombok.Builder
    @lombok.Data
    public static class JwtTokenInfo {
        private final boolean hasToken;
        private final Instant tokenExpiry;
        private final Instant lastRefresh;
        private final boolean isValid;
        private final long refreshCount;
        private final long validationCount;
        private final long errorCount;
        private final boolean autoRefreshEnabled;
        private final boolean fileWatchingEnabled;
        private final long refreshBufferSeconds;

        public long getTokenAgeSeconds() {
            return lastRefresh != null ?
                    java.time.Duration.between(lastRefresh, Instant.now()).getSeconds() : -1;
        }

        public long getSecondsUntilExpiry() {
            return tokenExpiry != null ?
                    java.time.Duration.between(Instant.now(), tokenExpiry).getSeconds() : -1;
        }

        public double getErrorRate() {
            long totalOperations = refreshCount + validationCount;
            return totalOperations > 0 ? (double) errorCount / totalOperations * 100 : 0.0;
        }
    }
}