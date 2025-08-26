// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.AccessTokenResponse;
import uk.gov.dbt.ndtp.federator.model.JwtToken;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling JWT token operations using Keycloak Admin Client.
 * Returns JwtToken objects for type-safe token handling.
 */
@Slf4j
public class JwtTokenService {

    private static final String KEYCLOAK_CLIENT_SECRET = "keycloak.client.secret";
    private static final String KEYCLOAK_REALM = "keycloak.realm";
    private static final String KEYCLOAK_SERVER_URL = "keycloak.server.url";
    private static final String KEYCLOAK_CLIENT_ID = "keycloak.client.id";
    private static final String TOKEN_DELIMITER = "\\.";
    private static final int EXPECTED_TOKEN_PARTS = 3;
    private static final int PAYLOAD_PART_INDEX = 1;
    private static final String EXP_CLAIM = "exp";
    private static final String CLIENT_ID_CLAIM = "client_id";
    private static final String SUBJECT_CLAIM = "sub";
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String KEYCLOAK_TOKEN_BUFFER_SECONDS = "keycloak.token.buffer.seconds";

    private final Keycloak keycloakClient;
    private final ObjectMapper objectMapper;
    private final int bufferSeconds;
    private final Map<String, JwtToken> tokenCache;

    /**
     * Constructs JwtTokenService with Keycloak configuration.
     *
     * @param objectMapper JSON object mapper for parsing
     */
    public JwtTokenService(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.tokenCache = new ConcurrentHashMap<>();
        this.bufferSeconds = PropertyUtil.getPropertyIntValue(KEYCLOAK_TOKEN_BUFFER_SECONDS);
        this.keycloakClient = initializeKeycloakClient();
        log.info("JwtTokenService initialized");
    }

    /**
     * Fetches JWT token from Keycloak as JwtToken object.
     *
     * @return JwtToken object with token and metadata
     * @throws IllegalStateException if fetch fails
     */
    public JwtToken fetchJwtToken() {
        try {
            final String clientId = PropertyUtil.getPropertyValue(KEYCLOAK_CLIENT_ID);

            final JwtToken cached = tokenCache.get(clientId);

            if (cached != null && !cached.shouldRefresh(bufferSeconds)) {
                log.debug("Using cached token for: {}", clientId);
                return cached;
            }

            final JwtToken newToken = createTokenFromKeycloak();
            tokenCache.put(clientId, newToken);
            log.debug("Fetched new token for: {}", clientId);
            return newToken;
        } catch (final Exception e) {
            log.error("Failed to fetch token: {}", e.getMessage());
            throw new IllegalStateException("Unable to fetch JWT token", e);
        }
    }

    /**
     * Validates if token string is well-formed and not expired.
     *
     * @param tokenString JWT token string
     * @return true if valid, false otherwise
     */
    public boolean isTokenValid(final String tokenString) {
        if (tokenString == null || tokenString.trim().isEmpty()) {
            return false;
        }
        final String[] parts = tokenString.split(TOKEN_DELIMITER);
        if (parts.length != EXPECTED_TOKEN_PARTS) {
            return false;
        }
        final Long expiry = extractExpiry(tokenString);
        return expiry != null && !isExpired(expiry);
    }

    /**
     * Gets remaining validity in seconds for token string.
     *
     * @param tokenString JWT token string
     * @return remaining seconds or -1 if invalid
     */
    public long getRemainingValidity(final String tokenString) {
        final Long expiry = extractExpiry(tokenString);
        return expiry == null ? -1L : Math.max(0, expiry - System.currentTimeMillis() / 1000);
    }

    /**
     * Creates JwtToken object from Keycloak response.
     *
     * @return populated JwtToken object
     */
    private JwtToken createTokenFromKeycloak() {
        final AccessTokenResponse response = keycloakClient.tokenManager().getAccessToken();
        final String tokenString = response.getToken();
        final Map<String, Object> claims = extractClaims(tokenString);

        return JwtToken.builder()
                .token(tokenString)
                .tokenType(TOKEN_TYPE_BEARER)
                .expiresAt(extractExpiry(tokenString))
                .clientId(extractClientIdFromClaims(claims))
                .claims(claims)
                .build();
    }

    /**
     * Initializes Keycloak client from properties.
     *
     * @return configured Keycloak client
     *
     */private Keycloak initializeKeycloakClient() {
        return KeycloakBuilder.builder()
                .serverUrl(PropertyUtil.getPropertyValue(KEYCLOAK_SERVER_URL))
                .realm(PropertyUtil.getPropertyValue(KEYCLOAK_REALM))
                .clientId(PropertyUtil.getPropertyValue(KEYCLOAK_CLIENT_ID))
                .clientSecret(PropertyUtil.getPropertyValue(KEYCLOAK_CLIENT_SECRET))
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    /**
     * Extracts expiry timestamp from token.
     *
     * @param tokenString JWT token string
     * @return expiry timestamp or null
     */
    private Long extractExpiry(final String tokenString) {
        final Map<String, Object> claims = extractClaims(tokenString);
        final Object exp = claims.get(EXP_CLAIM);
        return exp instanceof Number number ? number.longValue() : null;
    }

    /**
     * Checks if timestamp is expired.
     *
     * @param expiry expiry timestamp
     * @return true if expired
     */
    private boolean isExpired(final Long expiry) {
        return System.currentTimeMillis() / 1000 > (expiry - bufferSeconds);
    }

    /**
     * Extracts client ID from claims map.
     *
     * @param claims JWT claims map
     * @return client ID or null
     */
    private String extractClientIdFromClaims(final Map<String, Object> claims) {
        final Object clientId = claims.getOrDefault(CLIENT_ID_CLAIM, claims.get(SUBJECT_CLAIM));
        return clientId instanceof String string ? string : null;
    }

    /**
     * Parses JWT payload into claims map.
     *
     * @param tokenString JWT token string
     * @return claims map or empty map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractClaims(final String tokenString) {
        try {
            final String[] parts = tokenString.split(TOKEN_DELIMITER);
            if (parts.length != EXPECTED_TOKEN_PARTS) {
                return Map.of();
            }
            final byte[] payload = Base64.getUrlDecoder().decode(parts[PAYLOAD_PART_INDEX]);
            return objectMapper.readValue(new String(payload, StandardCharsets.UTF_8), Map.class);
        } catch (final Exception e) {
            log.debug("Claims extraction failed: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Closes Keycloak client connection.
     */
    public void close() {
        if (keycloakClient != null) {
            keycloakClient.close();
            tokenCache.clear();
            log.info("JwtTokenService closed");
        }
    }
}