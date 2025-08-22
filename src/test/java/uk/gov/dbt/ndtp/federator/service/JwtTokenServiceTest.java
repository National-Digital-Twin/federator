package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenService.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
class JwtTokenServiceTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private static final long VALID_EXPIRY = Instant.now().plusSeconds(3600).getEpochSecond();
    private static final long EXPIRED = Instant.now().minusSeconds(100).getEpochSecond();

    private JwtTokenService tokenService;
    private String validToken;
    private String expiredToken;

    /**
     * Sets up test fixtures before each test.
     */
    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(new ObjectMapper());
        validToken = createToken(CLIENT_ID, VALID_EXPIRY);
        expiredToken = createToken(CLIENT_ID, EXPIRED);
    }

    /**
     * Tests token validation.
     */
    @Test
    void testIsTokenValid() {
        assertTrue(tokenService.isTokenValid(validToken));
        assertFalse(tokenService.isTokenValid(expiredToken));
        assertFalse(tokenService.isTokenValid(null));
        assertFalse(tokenService.isTokenValid("invalid"));
    }

    /**
     * Tests client ID extraction.
     */
    @Test
    void testExtractClientId() {
        assertEquals(CLIENT_ID, tokenService.extractClientId(validToken));
        assertNull(tokenService.extractClientId("invalid"));
    }

    /**
     * Tests claims extraction.
     */
    @Test
    void testExtractClaims() {
        final Map<String, Object> claims = tokenService.extractClaims(validToken);
        assertNotNull(claims);
        assertFalse(claims.isEmpty());
        assertEquals(CLIENT_ID, claims.get("client_id"));

        assertTrue(tokenService.extractClaims("invalid").isEmpty());
    }

    /**
     * Tests remaining validity calculation.
     */
    @Test
    void testGetRemainingValidity() {
        assertTrue(tokenService.getRemainingValidity(validToken) > 0);
        assertEquals(-1, tokenService.getRemainingValidity(expiredToken));
    }

    /**
     * Creates a mock JWT token for testing.
     */
    private String createToken(final String clientId, final long expiry) {
        final String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        final String payload = String.format(
                "{\"client_id\":\"%s\",\"exp\":%d}", clientId, expiry);
        final String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        return header + "." + encodedPayload + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString("sig".getBytes());
    }
}