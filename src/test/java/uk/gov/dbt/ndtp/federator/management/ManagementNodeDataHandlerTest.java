package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ManagementNodeDataHandler.
 *
 * @author Rakesh Chiluka
 * @version 3.0
 * @since 2025-01-20
 */
@ExtendWith(MockitoExtension.class)
class ManagementNodeDataHandlerTest {

    private static final String BASE_URL = "https://localhost:8090";
    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String CLIENT_ID = "FEDERATOR_BCC";

    @Mock
    private HttpClient httpClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private JwtTokenService tokenService;
    @Mock
    private HttpResponse<String> httpResponse;
    @Mock
    private HttpResponse<Void> voidResponse;

    /**
     * Tests successful producer data retrieval.
     */
    @Test
    void testGetProducerData_Success() throws Exception {
        when(tokenService.fetchJwtToken()).thenReturn(VALID_TOKEN);
        when(tokenService.isTokenValid(VALID_TOKEN)).thenReturn(true);
        when(tokenService.getRemainingValidity(VALID_TOKEN)).thenReturn(3600L);

        final ProducerConfigDTO mockConfig = new ProducerConfigDTO();
        mockConfig.setClientId(CLIENT_ID);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(httpResponse);
        when(objectMapper.readValue(anyString(), eq(ProducerConfigDTO.class)))
                .thenReturn(mockConfig);

        final ManagementNodeDataHandler dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, BASE_URL, tokenService, Duration.ofSeconds(30));
        final ProducerConfigDTO result = dataHandler.getProducerData();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(tokenService).fetchJwtToken();
    }

    /**
     * Tests successful consumer data retrieval.
     */
    @Test
    void testGetConsumerData_Success() throws Exception {
        when(tokenService.fetchJwtToken()).thenReturn(VALID_TOKEN);
        when(tokenService.isTokenValid(VALID_TOKEN)).thenReturn(true);
        when(tokenService.getRemainingValidity(VALID_TOKEN)).thenReturn(3600L);

        final ConsumerConfigDTO mockConfig = new ConsumerConfigDTO();
        mockConfig.setClientId(CLIENT_ID);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(httpResponse);
        when(objectMapper.readValue(anyString(), eq(ConsumerConfigDTO.class)))
                .thenReturn(mockConfig);

        final ManagementNodeDataHandler dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, BASE_URL, tokenService, Duration.ofSeconds(30));
        final ConsumerConfigDTO result = dataHandler.getConsumerData();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(tokenService).fetchJwtToken();
    }

    /**
     * Tests handling of HTTP error responses.
     */
    @Test
    void testGetProducerData_HttpError() throws Exception {
        when(tokenService.fetchJwtToken()).thenReturn(VALID_TOKEN);
        when(tokenService.isTokenValid(VALID_TOKEN)).thenReturn(true);
        when(tokenService.getRemainingValidity(VALID_TOKEN)).thenReturn(3600L);
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(httpResponse);

        final ManagementNodeDataHandler dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, BASE_URL, tokenService, Duration.ofSeconds(30));

        assertThrows(IOException.class, () -> dataHandler.getProducerData());
    }

    /**
     * Tests handling of invalid token.
     */
    @Test
    void testGetProducerData_InvalidToken() throws IOException {
        when(tokenService.fetchJwtToken()).thenReturn(VALID_TOKEN);
        when(tokenService.isTokenValid(VALID_TOKEN)).thenReturn(false);

        final ManagementNodeDataHandler dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, BASE_URL, tokenService, Duration.ofSeconds(30));

        assertThrows(IOException.class, () -> dataHandler.getProducerData());
    }

    /**
     * Tests connectivity check when node is reachable.
     */
    @Test
    void testTestConnectivity_Success() throws Exception {
        when(voidResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.discarding()))).thenReturn(voidResponse);

        final ManagementNodeDataHandler dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, BASE_URL, tokenService, Duration.ofSeconds(30));

        assertTrue(dataHandler.testConnectivity());
    }

    /**
     * Tests connectivity check when node is unreachable.
     */
    @Test
    void testTestConnectivity_Failure() throws Exception {
        when(httpClient.send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.discarding())))
                .thenThrow(new IOException("Connection refused"));

        final ManagementNodeDataHandler dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, BASE_URL, tokenService, Duration.ofSeconds(30));

        assertFalse(dataHandler.testConnectivity());
    }

    /**
     * Tests constructor with null parameters.
     */
    @Test
    void testConstructor_NullParameters() {
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(
                        null, objectMapper, BASE_URL, tokenService));
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(
                        httpClient, null, BASE_URL, tokenService));
        assertThrows(IllegalArgumentException.class,
                () -> new ManagementNodeDataHandler(
                        httpClient, objectMapper, "", tokenService));
    }
}