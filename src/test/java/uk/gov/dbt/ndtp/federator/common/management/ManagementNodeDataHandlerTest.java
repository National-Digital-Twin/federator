// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Unit tests for ManagementNodeDataHandler.
 */
@ExtendWith(MockitoExtension.class)
class ManagementNodeDataHandlerTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private static final String VALID_TOKEN = "valid.token";
    private static final String ID = "id-123";
    private static final int HTTP_OK = 200;
    private static final int HTTP_ERROR = 401;
    private static final String BASE_URL = "https://localhost:8090";
    private static final String TIMEOUT = "30";
    private static final String BASE_URL_PROP = "management.node.base.url";
    private static final String TIMEOUT_PROP = "management.node.request.timeout";
    private static final String EMPTY_JSON = "{}";
    private static final String ERROR_401 = "401";
    private static final String ERROR_MSG = "error";
    private static final String EMPTY = "";

    @Mock
    private HttpClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IdpTokenService tokenService;

    @Mock
    private HttpResponse<String> httpResponse;

    private ManagementNodeDataHandler handler;
    private MockedStatic<PropertyUtil> propertyMock;

    @BeforeEach
    void setUp() {
        propertyMock = mockStatic(PropertyUtil.class);
        setupProperties();
        handler = new ManagementNodeDataHandler(httpClient, objectMapper, tokenService);
    }

    @AfterEach
    void tearDown() {
        if (propertyMock != null) {
            propertyMock.close();
        }
    }

    @Test
    void testProducerDataFetch() throws Exception {
        setupSuccess(createProducerConfig());
        final ProducerConfigDTO result = handler.getProducerData(ID);
        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(tokenService).fetchToken();
    }

    @Test
    void testConsumerDataFetch() throws Exception {
        setupSuccess(createConsumerConfig());
        final ConsumerConfigDTO result = handler.getConsumerData(null);
        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
    }

    @Test
    void testHttpError() throws Exception {
        mockToken();
        when(httpResponse.statusCode()).thenReturn(HTTP_ERROR);
        when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenReturn(httpResponse);
        final ManagementNodeDataException ex =
                assertThrows(ManagementNodeDataException.class, () -> handler.getProducerData(null));
        assertTrue(ex.getMessage().contains(ERROR_401));
    }

    @Test
    void testExceptions() throws Exception {
        mockToken();
        when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenThrow(new IOException(ERROR_MSG));
        assertThrows(ManagementNodeDataException.class, () -> handler.getProducerData(null));

        mockToken();
        mockHttp();
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new JsonProcessingException(ERROR_MSG) {});
        assertThrows(ManagementNodeDataException.class, () -> handler.getConsumerData(null));
    }

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> new ManagementNodeDataHandler(null, objectMapper, tokenService));
        assertThrows(NullPointerException.class, () -> new ManagementNodeDataHandler(httpClient, null, tokenService));
        assertThrows(NullPointerException.class, () -> new ManagementNodeDataHandler(httpClient, objectMapper, null));
    }

    @Test
    void testPropertyValidation() {
        propertyMock
                .when(() -> PropertyUtil.getPropertyValue(eq(BASE_URL_PROP), anyString()))
                .thenThrow(new RuntimeException(ERROR_MSG));
        assertThrows(
                IllegalStateException.class,
                () -> new ManagementNodeDataHandler(httpClient, objectMapper, tokenService));

        propertyMock
                .when(() -> PropertyUtil.getPropertyValue(eq(BASE_URL_PROP), anyString()))
                .thenReturn(EMPTY);
        assertThrows(
                IllegalStateException.class,
                () -> new ManagementNodeDataHandler(httpClient, objectMapper, tokenService));
    }

    @Test
    void testExecuteRequest_jsonProcessingException() throws Exception {
        mockToken();
        mockHttp();
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new JsonProcessingException("json error") {});

        assertThrows(ManagementNodeDataException.class, () -> handler.getProducerData(ID));
    }

    @Test
    void testValidateResponse_error() throws Exception {
        mockToken();
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenReturn(httpResponse);

        assertThrows(ManagementNodeDataException.class, () -> handler.getProducerData(ID));
    }

    private void setupProperties() {
        propertyMock
                .when(() -> PropertyUtil.getPropertyValue(eq(BASE_URL_PROP), anyString()))
                .thenReturn(BASE_URL);
        propertyMock
                .when(() -> PropertyUtil.getPropertyValue(eq(TIMEOUT_PROP), anyString()))
                .thenReturn(TIMEOUT);
    }

    private void setupSuccess(final Object config) throws Exception {
        mockToken();
        mockHttp();
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(config);
    }

    private void mockToken() {
        when(tokenService.fetchToken()).thenReturn(VALID_TOKEN);
    }

    private void mockHttp() throws IOException, InterruptedException {
        when(httpResponse.statusCode()).thenReturn(HTTP_OK);
        when(httpResponse.body()).thenReturn(EMPTY_JSON);
        when(httpClient.send(any(HttpRequest.class), any(BodyHandler.class))).thenReturn(httpResponse);
    }

    private ProducerConfigDTO createProducerConfig() {
        return ProducerConfigDTO.builder().clientId(CLIENT_ID).build();
    }

    private ConsumerConfigDTO createConsumerConfig() {
        return ConsumerConfigDTO.builder().clientId(CLIENT_ID).build();
    }
}
