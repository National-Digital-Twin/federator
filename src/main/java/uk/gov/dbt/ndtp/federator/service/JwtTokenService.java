package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service for handling JWT token operations including validation and expiry checks.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@Slf4j
public class JwtTokenService {

    private static final String TOKEN_DELIMITER = "\\.";
    private static final int TOKEN_PARTS = 3;
    private static final int PAYLOAD_INDEX = 1;
    private static final int BUFFER_SECONDS = 60;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final Properties appProperties;
    private final HttpClient httpClient;

    /**
     * Constructs a JwtTokenService with required dependencies.
     */
    public JwtTokenService(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.appProperties = loadProperties();
        this.httpClient = HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build();
        log.debug("JwtTokenService initialized");
    }

    /**
     * Fetches JWT token from Keycloak using client credentials.
     */
    public String fetchJwtToken() throws IOException {
        final String tokenUrl = appProperties.getProperty("keycloak.base.url") +
                appProperties.getProperty("keycloak.token.endpoint");
        final String formData = "client_id=" + URLEncoder.encode(
                appProperties.getProperty("keycloak.client.id"), StandardCharsets.UTF_8) +
                "&grant_type=client_credentials&client_secret=" + URLEncoder.encode(
                appProperties.getProperty("keycloak.client.secret"), StandardCharsets.UTF_8);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl)).timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData)).build();

        try {
            final HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Token request failed: " + response.statusCode());
            }
            return objectMapper.readTree(response.body()).get("access_token").asText();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Token request interrupted", e);
        }
    }

    /**
     * Validates if a JWT token is well-formed and not expired.
     */
    public boolean isTokenValid(final String jwtToken) {
        return isWellFormed(jwtToken) && !isTokenExpired(jwtToken);
    }

    /**
     * Checks if a JWT token is well-formed with proper structure.
     */
    public boolean isWellFormed(final String jwtToken) {
        if (jwtToken == null || jwtToken.trim().isEmpty()) {
            return false;
        }
        final String[] parts = jwtToken.split(TOKEN_DELIMITER);
        if (parts.length != TOKEN_PARTS) {
            return false;
        }
        try {
            Base64.getUrlDecoder().decode(parts[0]);
            Base64.getUrlDecoder().decode(parts[1]);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks if a JWT token has expired.
     */
    public boolean isTokenExpired(final String jwtToken) {
        final Long expiry = getTokenExpiry(jwtToken);
        if (expiry == null) {
            return true;
        }
        return Instant.now().getEpochSecond() > (expiry - BUFFER_SECONDS);
    }

    /**
     * Extracts the client ID from the JWT token.
     */
    public String extractClientId(final String jwtToken) {
        final Map<String, Object> claims = extractClaims(jwtToken);
        if (claims.isEmpty()) {
            return null;
        }
        Object clientId = claims.get("client_id");
        if (clientId == null) {
            clientId = claims.get("sub");
        }
        return clientId instanceof String ? (String) clientId : null;
    }

    /**
     * Extracts all claims from the JWT token payload.
     */
    public Map<String, Object> extractClaims(final String jwtToken) {
        if (!isWellFormed(jwtToken)) {
            return Collections.emptyMap();
        }
        try {
            final String[] parts = jwtToken.split(TOKEN_DELIMITER);
            final byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[PAYLOAD_INDEX]);
            final String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            final Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            return claims;
        } catch (final Exception e) {
            log.error("Failed to extract claims: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Gets the token expiry time in seconds since epoch.
     */
    public Long getTokenExpiry(final String jwtToken) {
        final Map<String, Object> claims = extractClaims(jwtToken);
        if (claims.isEmpty()) {
            return null;
        }
        final Object exp = claims.get("exp");
        return exp instanceof Number ? ((Number) exp).longValue() : null;
    }

    /**
     * Gets the remaining validity time of the token in seconds.
     */
    public long getRemainingValidity(final String jwtToken) {
        final Long expiry = getTokenExpiry(jwtToken);
        if (expiry == null) {
            return -1;
        }
        final long remaining = expiry - Instant.now().getEpochSecond();
        return remaining > 0 ? remaining : -1;
    }

    /**
     * Loads application properties from available locations.
     */
    private Properties loadProperties() {
        final Properties props = new Properties();
        final String[] locations = {"test.properties", "app.properties"};

        for (final String location : locations) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(location)) {
                if (input != null) {
                    props.load(input);
                    log.debug("Loaded {} from classpath", location);
                    return props;
                }
            } catch (final Exception e) {
                log.debug("{} not found", location);
            }
        }

        log.warn("No properties file found, using defaults");
        return props;
    }
}