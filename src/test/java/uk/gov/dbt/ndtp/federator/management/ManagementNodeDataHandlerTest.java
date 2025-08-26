// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.model.JwtToken;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;
import uk.gov.dbt.ndtp.federator.utils.CommonPropertiesLoader;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ManagementNodeDataHandler.
 */
@ExtendWith(MockitoExtension.class)
class ManagementNodeDataHandlerTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    @Mock private HttpClient httpClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private JwtTokenService tokenService;
    @Mock private HttpResponse<String> httpResponse;
    @Mock private HttpResponse<Void> voidResponse;
    @Mock private JwtToken jwtToken;

    private ManagementNodeDataHandler dataHandler;

    @BeforeEach
    void setUp() {
        CommonPropertiesLoader.loadTestProperties();
        dataHandler = new ManagementNodeDataHandler(httpClient, objectMapper, tokenService);
    }

    @Test
    void testGetProducerData_Success() throws Exception {
        mockSuccessfulTokenFetch();
        mockSuccessfulHttpCall();
        ProducerConfigDTO expected = createProducerConfig();
        when(objectMapper.readValue(anyString(), eq(ProducerConfigDTO.class)))
                .thenReturn(expected);

        ProducerConfigDTO result = dataHandler.getProducerData(Optional.empty());

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(tokenService).fetchJwtToken();
    }

    @Test
    void testGetConsumerData_Success() throws Exception {
        mockSuccessfulTokenFetch();
        mockSuccessfulHttpCall();
        ConsumerConfigDTO expected = createConsumerConfig();
        when(objectMapper.readValue(anyString(), eq(ConsumerConfigDTO.class)))
                .thenReturn(expected);

        ConsumerConfigDTO result = dataHandler.getConsumerData(Optional.empty());

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(tokenService).fetchJwtToken();
    }

    @Test
    void testGetProducerData_WithId() throws Exception {
        mockSuccessfulTokenFetch();
        mockSuccessfulHttpCall();
        ProducerConfigDTO expected = createProducerConfig();
        when(objectMapper.readValue(anyString(), eq(ProducerConfigDTO.class)))
                .thenReturn(expected);

        ProducerConfigDTO result = dataHandler.getProducerData(Optional.of(CLIENT_ID));

        assertNotNull(result);
        verify(tokenService).fetchJwtToken();
    }

    @Test
    void testGetProducerData_HttpError() throws Exception {
        mockSuccessfulTokenFetch();
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        assertThrows(IOException.class, () ->
                dataHandler.getProducerData(Optional.empty()));
    }

    @Test
    void testGetProducerData_TokenExpired() {
        when(tokenService.fetchJwtToken()).thenReturn(jwtToken);
        when(jwtToken.isExpired()).thenReturn(true);

        assertThrows(IOException.class, () ->
                dataHandler.getProducerData(Optional.empty()));
    }

    @Test
    void testCheckConnectivity_Success() throws Exception {
        when(voidResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.discarding())))
                .thenReturn(voidResponse);

        assertTrue(dataHandler.checkConnectivity());
    }

    @Test
    void testCheckConnectivity_Failure() throws Exception {
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.discarding())))
                .thenThrow(new IOException("Connection refused"));

        assertFalse(dataHandler.checkConnectivity());
    }

    @Test
    void testCheckConnectivity_Interrupted() throws Exception {
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.discarding())))
                .thenThrow(new InterruptedException("Interrupted"));

        assertFalse(dataHandler.checkConnectivity());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void testIsConfigured() {
        assertTrue(dataHandler.isConfigured());
    }

    @Test
    void testGetRequestTimeoutSeconds() {
        assertEquals(30, dataHandler.getRequestTimeoutSeconds());
    }

    @Test
    void testConstructor_NullParameters() {
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(null, objectMapper, tokenService));
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(httpClient, null, tokenService));
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(httpClient, objectMapper, null));
    }

    private void mockSuccessfulTokenFetch() {
        when(tokenService.fetchJwtToken()).thenReturn(jwtToken);
        when(jwtToken.getToken()).thenReturn("valid.token");
        when(jwtToken.isExpired()).thenReturn(false);
        when(jwtToken.getRemainingValidity()).thenReturn(3600L);
    }

    private void mockSuccessfulHttpCall() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
    }

    private ProducerConfigDTO createProducerConfig() {
        ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }

    private ConsumerConfigDTO createConsumerConfig() {
        ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }
}