// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * JWT Token object containing the token string and metadata.
 * Provides convenient access to token properties without repeated parsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtToken {

    /**
     * The raw JWT token string.
     */
    private String token;

    /**
     * Token expiry timestamp in seconds since epoch.
     */
    private Long expiresAt;

    /**
     * Token type (typically "Bearer").
     */
    private String tokenType;

    /**
     * Client ID from token claims.
     */
    private String clientId;

    /**
     * All claims from the JWT payload.
     */
    private Map<String, Object> claims;

    /**
     * Checks if the token is expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return true;
        }
        return Instant.now().getEpochSecond() > expiresAt;
    }

    /**
     * Gets remaining validity in seconds.
     *
     * @return remaining seconds or 0 if expired
     */
    public long getRemainingValidity() {
        if (expiresAt == null) {
            return 0;
        }
        final long remaining = expiresAt - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /**
     * Checks if token needs refresh based on buffer time.
     *
     * @param bufferSeconds seconds before expiry to consider refresh
     * @return true if should refresh, false otherwise
     */
    public boolean shouldRefresh(final int bufferSeconds) {
        return getRemainingValidity() <= bufferSeconds;
    }
}