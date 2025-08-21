package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ManagementNodeDataHandler}.
 *
 * <p>This test class provides comprehensive coverage for:
 * <ul>
 *   <li>Producer and consumer configuration retrieval</li>
 *   <li>JWT token validation</li>
 *   <li>HTTP error handling</li>
 *   <li>Connectivity testing</li>
 *   <li>Edge cases and error scenarios</li>
 * </ul>
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-01-20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Management Node Data Handler Tests")
class ManagementNodeDataHandlerTest {

    private static final String BASE_URL = "https://localhost:8090";
    private static final String PRODUCER_ENDPOINT = "/api/v1/configuration/producer";
    private static final String CONSUMER_ENDPOINT = "/api/v1/configuration/consumer";

    private static final String VALID_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
    private static final String INVALID_JWT = "invalid.jwt.token";
    private static final String EMPTY_JWT = "";
    private static final String CLIENT_ID = "FEDERATOR_BCC";

    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SERVER_ERROR = 500;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private ObjectMapper mockObjectMapper;

    @Mock
    private JwtTokenService mockTokenService;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    @Mock
    private HttpResponse<Void> mockVoidResponse;

    private ManagementNodeDataHandler dataHandler;

    /**
     * Sets up test fixtures before each test method.
     * Initializes the handler with mocked dependencies.
     */
    @BeforeEach
    void setUp() {
        dataHandler = new ManagementNodeDataHandler(
                mockHttpClient,
                mockObjectMapper,
                BASE_URL,
                mockTokenService,
                TIMEOUT
        );
    }

    /**
     * Tests for constructor validation.
     */
    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw NullPointerException when HttpClient is null")
        void shouldThrowExceptionWhenHttpClientIsNull() {
            assertThrows(NullPointerException.class,
                    () -> new ManagementNodeDataHandler(null, mockObjectMapper, BASE_URL, mockTokenService),
                    "Should throw NullPointerException for null HttpClient");
        }

        @Test
        @DisplayName("Should throw NullPointerException when ObjectMapper is null")
        void shouldThrowExceptionWhenObjectMapperIsNull() {
            assertThrows(NullPointerException.class,
                    () -> new ManagementNodeDataHandler(mockHttpClient, null, BASE_URL, mockTokenService),
                    "Should throw NullPointerException for null ObjectMapper");
        }

        @Test
        @DisplayName("Should throw NullPointerException when base URL is null")
        void shouldThrowExceptionWhenBaseUrlIsNull() {
            assertThrows(NullPointerException.class,
                    () -> new ManagementNodeDataHandler(mockHttpClient, mockObjectMapper, null, mockTokenService),
                    "Should throw NullPointerException for null base URL");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when base URL is empty")
        void shouldThrowExceptionWhenBaseUrlIsEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ManagementNodeDataHandler(mockHttpClient, mockObjectMapper, "", mockTokenService),
                    "Should throw IllegalArgumentException for empty base URL");
        }

        @Test
        @DisplayName("Should remove trailing slash from base URL")
        void shouldRemoveTrailingSlashFromBaseUrl() {
            final ManagementNodeDataHandler handler = new ManagementNodeDataHandler(
                    mockHttpClient,
                    mockObjectMapper,
                    BASE_URL + "/",
                    mockTokenService
            );
            assertNotNull(handler, "Handler should be created successfully");
        }
    }

    /**
     * Tests for producer configuration retrieval.
     */
    @Nested
    @DisplayName("Producer Configuration Tests")
    class ProducerConfigurationTests {

        @Test
        @DisplayName("Should fetch producer configuration successfully with valid JWT")
        void shouldFetchProducerConfigurationSuccessfully() throws Exception {
            // Arrange
            final String responseJson = "{\"clientId\":\"FEDERATOR_BCC\",\"producers\":[]}";
            final ProducerConfigDTO expectedConfig = ProducerConfigDTO.builder()
                    .clientId(CLIENT_ID)
                    .producers(Collections.emptyList())
                    .build();

            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_OK);
            when(mockHttpResponse.body()).thenReturn(responseJson);
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);
            when(mockObjectMapper.readValue(responseJson, ProducerConfigDTO.class))
                    .thenReturn(expectedConfig);

            // Act
            final ProducerConfigDTO result = dataHandler.getProducerData(VALID_JWT,null);

            // Assert
            assertNotNull(result, "Result should not be null");
            assertEquals(CLIENT_ID, result.getClientId(), "Client ID should match");
            assertNotNull(result.getProducers(), "Producers list should not be null");

            // Verify
            verify(mockTokenService).isTokenValid(VALID_JWT);
            verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            verify(mockObjectMapper).readValue(responseJson, ProducerConfigDTO.class);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null JWT token")
        void shouldThrowExceptionForNullToken() {
            assertThrows(IllegalArgumentException.class,
                    () -> dataHandler.getProducerData(null,null),
                    "Should throw IllegalArgumentException for null token");

            verifyNoInteractions(mockHttpClient);
            verifyNoInteractions(mockObjectMapper);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty JWT token")
        void shouldThrowExceptionForEmptyToken() {
            assertThrows(IllegalArgumentException.class,
                    () -> dataHandler.getProducerData(EMPTY_JWT,null),
                    "Should throw IllegalArgumentException for empty token");

            verifyNoInteractions(mockHttpClient);
            verifyNoInteractions(mockObjectMapper);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid JWT token")
        void shouldThrowExceptionForInvalidToken() {
            when(mockTokenService.isTokenValid(INVALID_JWT)).thenReturn(false);
            when(mockTokenService.extractClientId(INVALID_JWT)).thenReturn(CLIENT_ID);

            assertThrows(IllegalArgumentException.class,
                    () -> dataHandler.getProducerData(INVALID_JWT,null),
                    "Should throw IllegalArgumentException for invalid token");

            verify(mockTokenService).isTokenValid(INVALID_JWT);
            verifyNoInteractions(mockHttpClient);
        }

        @Test
        @DisplayName("Should handle HTTP 401 Unauthorized response")
        void shouldHandleUnauthorizedResponse() throws Exception {
            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_UNAUTHORIZED);
            when(mockHttpResponse.body()).thenReturn("Unauthorized");
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);

            final IOException exception = assertThrows(IOException.class,
                    () -> dataHandler.getProducerData(VALID_JWT,null),
                    "Should throw IOException for unauthorized response");

            assertTrue(exception.getMessage().contains("Authentication failed"),
                    "Exception message should indicate authentication failure");
        }
    }

    /**
     * Tests for consumer configuration retrieval.
     */
    @Nested
    @DisplayName("Consumer Configuration Tests")
    class ConsumerConfigurationTests {

        @Test
        @DisplayName("Should fetch consumer configuration successfully with valid JWT")
        void shouldFetchConsumerConfigurationSuccessfully() throws Exception {
            // Arrange
            final String responseJson = "{\"clientId\":\"FEDERATOR_HEG\",\"producers\":[]}";
            final ConsumerConfigDTO expectedConfig = ConsumerConfigDTO.builder()
                    .clientId("FEDERATOR_HEG")
                    .producers(Collections.emptyList())
                    .build();

            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_OK);
            when(mockHttpResponse.body()).thenReturn(responseJson);
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);
            when(mockObjectMapper.readValue(responseJson, ConsumerConfigDTO.class))
                    .thenReturn(expectedConfig);

            // Act
            final ConsumerConfigDTO result = dataHandler.getConsumerData(VALID_JWT, null);

            // Assert
            assertNotNull(result, "Result should not be null");
            assertEquals("FEDERATOR_HEG", result.getClientId(), "Client ID should match");
            assertNotNull(result.getProducers(), "Producers list should not be null");

            // Verify
            verify(mockTokenService).isTokenValid(VALID_JWT);
            verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            verify(mockObjectMapper).readValue(responseJson, ConsumerConfigDTO.class);
        }

        @Test
        @DisplayName("Should handle HTTP 404 Not Found response")
        void shouldHandleNotFoundResponse() throws Exception {
            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_NOT_FOUND);
            when(mockHttpResponse.body()).thenReturn("Not Found");
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);

            final IOException exception = assertThrows(IOException.class,
                    () -> dataHandler.getConsumerData(VALID_JWT, null),
                    "Should throw IOException for not found response");

            assertTrue(exception.getMessage().contains("Endpoint not found"),
                    "Exception message should indicate endpoint not found");
        }

        @Test
        @DisplayName("Should handle HTTP 500 Server Error response")
        void shouldHandleServerErrorResponse() throws Exception {
            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_SERVER_ERROR);
            when(mockHttpResponse.body()).thenReturn("Internal Server Error");
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);

            final IOException exception = assertThrows(IOException.class,
                    () -> dataHandler.getConsumerData(VALID_JWT, null),
                    "Should throw IOException for server error response");

            assertTrue(exception.getMessage().contains("Server error"),
                    "Exception message should indicate server error");
        }
    }

    /**
     * Tests for connectivity testing functionality.
     */
    @Nested
    @DisplayName("Connectivity Tests")
    class ConnectivityTests {

        @Test
        @DisplayName("Should return true when management node is reachable")
        void shouldReturnTrueWhenNodeIsReachable() throws Exception {
            when(mockVoidResponse.statusCode()).thenReturn(HTTP_OK);
            when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.discarding())))
                    .thenReturn(mockVoidResponse);

            final boolean result = dataHandler.testConnectivity();

            assertTrue(result, "Should return true when node is reachable");
            verify(mockHttpClient).send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.discarding()));
        }

        @Test
        @DisplayName("Should return false when management node returns server error")
        void shouldReturnFalseWhenNodeReturnsServerError() throws Exception {
            when(mockVoidResponse.statusCode()).thenReturn(HTTP_SERVER_ERROR);
            when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.discarding())))
                    .thenReturn(mockVoidResponse);

            final boolean result = dataHandler.testConnectivity();

            assertFalse(result, "Should return false when node returns server error");
        }

        @Test
        @DisplayName("Should return false when connection fails")
        void shouldReturnFalseWhenConnectionFails() throws Exception {
            when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.discarding())))
                    .thenThrow(new IOException("Connection refused"));

            final boolean result = dataHandler.testConnectivity();

            assertFalse(result, "Should return false when connection fails");
        }
    }

    /**
     * Tests for HTTP request building and headers.
     */
    @Nested
    @DisplayName("HTTP Request Tests")
    class HttpRequestTests {

        @Test
        @DisplayName("Should build request with correct headers")
        void shouldBuildRequestWithCorrectHeaders() throws Exception {
            // Arrange
            final ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_OK);
            when(mockHttpResponse.body()).thenReturn("{}");
            when(mockHttpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);
            when(mockObjectMapper.readValue("{}", ProducerConfigDTO.class))
                    .thenReturn(ProducerConfigDTO.builder().clientId(CLIENT_ID).build());

            // Act
            dataHandler.getProducerData(VALID_JWT,null);

            // Assert
            final HttpRequest capturedRequest = requestCaptor.getValue();
            assertNotNull(capturedRequest, "Request should not be null");
            assertEquals(BASE_URL + PRODUCER_ENDPOINT, capturedRequest.uri().toString(),
                    "Request URI should match");

            assertTrue(capturedRequest.headers().firstValue("Authorization").isPresent(),
                    "Authorization header should be present");
            assertEquals("Bearer " + VALID_JWT,
                    capturedRequest.headers().firstValue("Authorization").get(),
                    "Authorization header should contain Bearer token");

            assertTrue(capturedRequest.headers().firstValue("Content-Type").isPresent(),
                    "Content-Type header should be present");
            assertEquals("application/json",
                    capturedRequest.headers().firstValue("Content-Type").get(),
                    "Content-Type should be application/json");
        }

        @Test
        @DisplayName("Should set correct timeout for requests")
        void shouldSetCorrectTimeout() throws Exception {
            final ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_OK);
            when(mockHttpResponse.body()).thenReturn("{}");
            when(mockHttpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);
            when(mockObjectMapper.readValue("{}", ProducerConfigDTO.class))
                    .thenReturn(ProducerConfigDTO.builder().clientId(CLIENT_ID).build());

            dataHandler.getProducerData(VALID_JWT,null);

            final HttpRequest capturedRequest = requestCaptor.getValue();
            assertTrue(capturedRequest.timeout().isPresent(), "Timeout should be present");
            assertEquals(TIMEOUT, capturedRequest.timeout().get(), "Timeout should match");
        }
    }

    /**
     * Tests for error handling scenarios.
     */
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle JSON parsing exception")
        void shouldHandleJsonParsingException() throws Exception {
            // Arrange - Create a real ObjectMapper that will fail to parse invalid JSON
            final ObjectMapper realObjectMapper = new ObjectMapper();
            final ManagementNodeDataHandler handlerWithRealMapper = new ManagementNodeDataHandler(
                    mockHttpClient,
                    realObjectMapper,  // Use real ObjectMapper
                    BASE_URL,
                    mockTokenService,
                    TIMEOUT
            );

            final String invalidJson = "{ invalid json }";  // Invalid JSON structure

            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpResponse.statusCode()).thenReturn(HTTP_OK);
            when(mockHttpResponse.body()).thenReturn(invalidJson);
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockHttpResponse);

            // Act & Assert
            final IOException exception = assertThrows(IOException.class,
                    () -> handlerWithRealMapper.getProducerData(VALID_JWT,null),
                    "Should throw IOException for JSON parsing error");

            assertTrue(exception.getMessage().contains("Failed to parse response"),
                    "Exception message should indicate parsing failure");
        }

        @Test
        @DisplayName("Should handle interrupted exception")
        void shouldHandleInterruptedException() throws Exception {
            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new InterruptedException("Request interrupted"));

            assertThrows(InterruptedException.class,
                    () -> dataHandler.getProducerData(VALID_JWT,null),
                    "Should propagate InterruptedException");
        }

        @Test
        @DisplayName("Should handle network IOException")
        void shouldHandleNetworkIOException() throws Exception {
            when(mockTokenService.isTokenValid(VALID_JWT)).thenReturn(true);
            when(mockTokenService.getRemainingValidity(VALID_JWT)).thenReturn(3600L);
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Network error"));

            assertThrows(IOException.class,
                    () -> dataHandler.getProducerData(VALID_JWT,null),
                    "Should propagate IOException");
        }
    }
}