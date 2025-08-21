package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Service for handling JWT token operations including validation and expiry checks.
 * Provides thread-safe JWT token management for the federator system.
 *
 * <p>This service handles:
 * <ul>
 *   <li>JWT token format validation</li>
 *   <li>Token expiry verification</li>
 *   <li>Client ID extraction from tokens</li>
 *   <li>Token claim parsing</li>
 * </ul>
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-01-20
 */
@Slf4j
public class JwtTokenService {

    private static final String TOKEN_DELIMITER = "\\.";
    private static final int TOKEN_PARTS = 3;
    private static final int HEADER_INDEX = 0;
    private static final int PAYLOAD_INDEX = 1;
    private static final int SIGNATURE_INDEX = 2;

    private static final String EXPIRY_CLAIM = "exp";
    private static final String CLIENT_ID_CLAIM = "client_id";
    private static final String SUBJECT_CLAIM = "sub";
    private static final String ISSUED_AT_CLAIM = "iat";

    private static final int BUFFER_SECONDS = 60;

    private final ObjectMapper objectMapper;

    /**
     * Constructs a JwtTokenService with the required dependencies.
     *
     * @param objectMapper JSON object mapper for parsing JWT payloads
     * @throws NullPointerException if objectMapper is null
     */
    public JwtTokenService(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper,
                "ObjectMapper cannot be null");
        log.debug("JwtTokenService initialized");
    }

    /**
     * Validates if a JWT token is well-formed and not expired.
     *
     * @param jwtToken the JWT token to validate
     * @return true if the token is valid and not expired, false otherwise
     */
    public boolean isTokenValid(final String jwtToken) {
        if (!isWellFormed(jwtToken)) {
            return false;
        }

        return !isTokenExpired(jwtToken);
    }

    /**
     * Checks if a JWT token is well-formed with proper structure.
     *
     * @param jwtToken the JWT token to check
     * @return true if the token has valid JWT structure, false otherwise
     */
    public boolean isWellFormed(final String jwtToken) {
        if (jwtToken == null || jwtToken.trim().isEmpty()) {
            log.debug("JWT token is null or empty");
            return false;
        }

        final String[] parts = jwtToken.split(TOKEN_DELIMITER);
        if (parts.length != TOKEN_PARTS) {
            log.debug("Invalid JWT format - expected {} parts, got {}",
                    TOKEN_PARTS, parts.length);
            return false;
        }

        // Validate each part is Base64 URL encoded
        try {
            Base64.getUrlDecoder().decode(parts[HEADER_INDEX]);
            Base64.getUrlDecoder().decode(parts[PAYLOAD_INDEX]);
            // Signature validation would require the secret key
            return true;
        } catch (final IllegalArgumentException e) {
            log.debug("Invalid Base64 encoding in JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a JWT token has expired based on the exp claim.
     * Includes a buffer period to handle clock skew and network delays.
     *
     * @param jwtToken the JWT token to check
     * @return true if the token is expired or will expire soon, false otherwise
     */
    public boolean isTokenExpired(final String jwtToken) {
        try {
            final Map<String, Object> claims = extractClaims(jwtToken);

            if (claims == null || !claims.containsKey(EXPIRY_CLAIM)) {
                log.debug("No expiry claim found in token");
                return true;
            }

            final Long expiry = extractLongClaim(claims, EXPIRY_CLAIM);
            if (expiry == null) {
                log.debug("Invalid expiry claim format");
                return true;
            }

            final long currentTime = Instant.now().getEpochSecond();
            final boolean expired = currentTime > (expiry - BUFFER_SECONDS);

            if (expired) {
                log.debug("Token expired or will expire within {} seconds", BUFFER_SECONDS);
            }

            return expired;
        } catch (final Exception e) {
            log.error("Error checking token expiry: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Extracts the client ID from the JWT token.
     * Checks both 'client_id' and 'sub' claims.
     *
     * @param jwtToken the JWT token to parse
     * @return the client ID from the token, or null if extraction fails
     */
    public String extractClientId(final String jwtToken) {
        try {
            final Map<String, Object> claims = extractClaims(jwtToken);

            if (claims == null) {
                return null;
            }

            // Try client_id claim first
            String clientId = extractStringClaim(claims, CLIENT_ID_CLAIM);

            // Fallback to subject claim
            if (clientId == null) {
                clientId = extractStringClaim(claims, SUBJECT_CLAIM);
            }

            if (clientId != null) {
                log.debug("Extracted client ID: {}", clientId);
            }

            return clientId;
        } catch (final Exception e) {
            log.error("Failed to extract client ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts all claims from the JWT token payload.
     *
     * @param jwtToken the JWT token to parse
     * @return map of claims, or null if extraction fails
     */
    public Map<String, Object> extractClaims(final String jwtToken) {
        if (!isWellFormed(jwtToken)) {
            return null;
        }

        try {
            final String[] parts = jwtToken.split(TOKEN_DELIMITER);
            final byte[] payloadBytes = Base64.getUrlDecoder()
                    .decode(parts[PAYLOAD_INDEX]);
            final String payload = new String(payloadBytes, StandardCharsets.UTF_8);

            @SuppressWarnings("unchecked")
            final Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            return claims;
        } catch (final Exception e) {
            log.error("Failed to extract claims from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the token expiry time in seconds since epoch.
     *
     * @param jwtToken the JWT token to check
     * @return expiry time in seconds since epoch, or null if not available
     */
    public Long getTokenExpiry(final String jwtToken) {
        try {
            final Map<String, Object> claims = extractClaims(jwtToken);
            return claims != null ? extractLongClaim(claims, EXPIRY_CLAIM) : null;
        } catch (final Exception e) {
            log.error("Failed to get token expiry: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the remaining validity time of the token in seconds.
     *
     * @param jwtToken the JWT token to check
     * @return remaining seconds until expiry, or -1 if expired or invalid
     */
    public long getRemainingValidity(final String jwtToken) {
        final Long expiry = getTokenExpiry(jwtToken);

        if (expiry == null) {
            return -1;
        }

        final long currentTime = Instant.now().getEpochSecond();
        final long remaining = expiry - currentTime;

        return remaining > 0 ? remaining : -1;
    }

    /**
     * Extracts a string claim from the claims map.
     *
     * @param claims the claims map
     * @param claimName the name of the claim to extract
     * @return the string value of the claim, or null if not found
     */
    private String extractStringClaim(final Map<String, Object> claims,
                                      final String claimName) {
        final Object value = claims.get(claimName);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Extracts a long claim from the claims map.
     *
     * @param claims the claims map
     * @param claimName the name of the claim to extract
     * @return the long value of the claim, or null if not found
     */
    private Long extractLongClaim(final Map<String, Object> claims,
                                  final String claimName) {
        final Object value = claims.get(claimName);

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return null;
    }
}