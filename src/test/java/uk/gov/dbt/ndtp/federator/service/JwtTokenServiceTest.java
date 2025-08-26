// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.utils.CommonPropertiesLoader;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenService token validation methods.
 */
class JwtTokenServiceTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private static final String REALM = "test-realm";
    private JwtTokenService service;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        CommonPropertiesLoader.loadTestProperties();
        secretKey = Keys.hmacShaKeyFor(
                "your-256-bit-secret-key-for-testing-purposes-only!!".getBytes()
        );
        service = new JwtTokenService(new ObjectMapper());
    }

    @Test
    void testIsTokenValid() {
        assertTrue(service.isTokenValid(createValidToken()));
        assertFalse(service.isTokenValid(createExpiredToken()));
        assertFalse(service.isTokenValid(null));
        assertFalse(service.isTokenValid(""));
        assertFalse(service.isTokenValid("invalid.token"));
    }

    @Test
    void testGetRemainingValidity() {
        String validToken = createValidToken();
        long remaining = service.getRemainingValidity(validToken);
        assertTrue(remaining > 3500 && remaining <= 3600);
        assertEquals(0L, service.getRemainingValidity(createExpiredToken()));
        assertEquals(-1L, service.getRemainingValidity("invalid"));
    }

    @Test
    void testTokenValidityEdgeCases() {
        assertFalse(service.isTokenValid(createTokenWithoutExpiry()));
        assertTrue(service.isTokenValid(createTokenWithInvalidSignature()));
        assertEquals(-1L, service.getRemainingValidity(createTokenWithoutExpiry()));
    }

    @Test
    void testTokenWithMultipleClaims() {
        String token = createTokenWithFullClaims();
        assertTrue(service.isTokenValid(token));
        assertTrue(service.getRemainingValidity(token) > 0);
    }

    private String createValidToken() {
        return Jwts.builder()
                .claim("client_id", CLIENT_ID)
                .subject(CLIENT_ID)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey)
                .compact();
    }

    private String createExpiredToken() {
        return Jwts.builder()
                .claim("client_id", CLIENT_ID)
                .subject(CLIENT_ID)
                .issuedAt(Date.from(Instant.now().minusSeconds(3700)))
                .expiration(Date.from(Instant.now().minusSeconds(100)))
                .signWith(secretKey)
                .compact();
    }

    private String createTokenWithoutExpiry() {
        return Jwts.builder()
                .claim("client_id", CLIENT_ID)
                .subject(CLIENT_ID)
                .issuedAt(new Date())
                .signWith(secretKey)
                .compact();
    }

    private String createTokenWithInvalidSignature() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "different-256-bit-secret-key-for-testing-only!!".getBytes()
        );
        return Jwts.builder()
                .claim("client_id", CLIENT_ID)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();
    }

    private String createTokenWithFullClaims() {
        return Jwts.builder()
                .claim("client_id", CLIENT_ID)
                .claim("azp", CLIENT_ID)
                .claim("scope", "openid profile email")
                .claim("typ", "Bearer")
                .claim("jti", UUID.randomUUID().toString())
                .issuer("https://localhost:8080/realms/" + REALM)
                .subject(CLIENT_ID)
                .audience().add("account").and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey)
                .compact();
    }
}