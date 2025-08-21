package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtTokenService}.
 *
 * <p>This test class validates JWT token operations including validation,
 * expiry checking, and client ID extraction using JUnit 5 and Mockito.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Token Service Tests")
class JwtTokenServiceTest {

    private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjk5OTk5OTk5OTksImNsaWVudF9pZCI6InRlc3QtY2xpZW50In0.signature";
    private static final String EXPIRED_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjEwMDAwMDAwMDAsImNsaWVudF9pZCI6ImV4cGlyZWQtY2xpZW50In0.signature";
    private static final String MALFORMED_TOKEN = "invalid.token";
    private static final String NULL_TOKEN = null;
    private static final String EMPTY_TOKEN = "";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String EXPIRED_CLIENT_ID = "expired-client";
    private static final long FUTURE_EXPIRY = 9999999999L;
    private static final long PAST_EXPIRY = 1000000000L;

    @Mock
    private ObjectMapper objectMapper;

    private JwtTokenService jwtTokenService;

    /**
     * Sets up test fixtures before each test method.
     * Initializes the service with a mocked ObjectMapper.
     */
    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(objectMapper);
    }

    /**
     * Tests validation of various JWT token scenarios.
     * Validates null, empty, malformed, expired and valid tokens.
     *
     * @throws Exception if JSON processing fails
     */
    @Test
    @DisplayName("Should validate token correctly for different scenarios")
    void testTokenValidation() throws Exception {
        // Test null and empty tokens (no mocking needed)
        assertFalse(jwtTokenService.isTokenValid(NULL_TOKEN), "Null token should return false");
        assertFalse(jwtTokenService.isTokenValid(EMPTY_TOKEN), "Empty token should return false");
        assertFalse(jwtTokenService.isTokenValid(MALFORMED_TOKEN), "Malformed token should return false");

        // Test valid token
        final Map<String, Object> validClaims = new HashMap<>();
        validClaims.put("exp", FUTURE_EXPIRY);
        validClaims.put("client_id", TEST_CLIENT_ID);

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(validClaims);
        assertTrue(jwtTokenService.isTokenValid(VALID_TOKEN), "Valid token should return true");

        // Reset mock for expired token test
        reset(objectMapper);

        // Test expired token
        final Map<String, Object> expiredClaims = new HashMap<>();
        expiredClaims.put("exp", PAST_EXPIRY);
        expiredClaims.put("client_id", EXPIRED_CLIENT_ID);

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(expiredClaims);
        assertFalse(jwtTokenService.isTokenValid(EXPIRED_TOKEN), "Expired token should return false");
    }

    /**
     * Tests client ID extraction and expiry checking.
     * Validates successful extraction and error handling.
     *
     * @throws Exception if JSON processing fails
     */
    @Test
    @DisplayName("Should extract client ID and check expiry correctly")
    void testExtractClientIdAndExpiry() throws Exception {
        // Test successful client ID extraction
        final Map<String, Object> claims = new HashMap<>();
        claims.put("exp", FUTURE_EXPIRY);
        claims.put("client_id", TEST_CLIENT_ID);

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(claims);

        final String clientId = jwtTokenService.extractClientId(VALID_TOKEN);
        assertNotNull(clientId, "Client ID should not be null");
        assertEquals(TEST_CLIENT_ID, clientId, "Client ID should match expected value");

        // Test expiry check for valid token
        assertFalse(jwtTokenService.isTokenExpired(VALID_TOKEN), "Valid token should not be expired");

        // Reset mock for error handling test
        reset(objectMapper);

        // Test error handling in client ID extraction
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Parse error"));

        assertNull(jwtTokenService.extractClientId(MALFORMED_TOKEN), "Should return null for invalid token");
        assertTrue(jwtTokenService.isTokenExpired(MALFORMED_TOKEN), "Should return true for unparseable token");

        // Test with expired token
        reset(objectMapper);
        final Map<String, Object> expiredClaims = new HashMap<>();
        expiredClaims.put("exp", PAST_EXPIRY);
        expiredClaims.put("client_id", EXPIRED_CLIENT_ID);

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(expiredClaims);

        assertTrue(jwtTokenService.isTokenExpired(EXPIRED_TOKEN), "Expired token should return true");
        assertEquals(EXPIRED_CLIENT_ID, jwtTokenService.extractClientId(EXPIRED_TOKEN),
                "Should extract client ID even from expired token");
    }
}