// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ManagementNodeDataHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ManagementNodeDataHandler Tests")
public class ManagementNodeDataHandlerTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private static final String VALID_TOKEN = "valid.token";
    private static final String NEW_TOKEN = "new.token";
    private static final int HTTP_OK = 200;
    private static final int HTTP_ERROR = 401;
    private static final String BASE_URL = "https://localhost:8090";
    private static final String PRODUCER_PATH =
            "/api/v1/configuration/producer";
    private static final String CONSUMER_PATH =
            "/api/v1/configuration/consumer";
    private static final String TIMEOUT = "30";
    private static final String CONFIG_KEY = "common.configuration";

    @Mock
    private HttpClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IdpTokenService tokenService;

    @Mock
    private HttpResponse<String> httpResponse;

    private ManagementNodeDataHandler dataHandler;
    private MockedStatic<PropertyUtil> propertyUtilMock;

    @BeforeEach
    void setUp() {
        propertyUtilMock = mockStatic(PropertyUtil.class);
        Properties props = createValidProperties();
        propertyUtilMock.when(() ->
                        PropertyUtil.getPropertiesFromAbsoluteFilePath(CONFIG_KEY))
                .thenReturn(props);
        dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, tokenService);
    }

    @AfterEach
    void tearDown() {
        if (propertyUtilMock != null) {
            propertyUtilMock.close();
        }
    }

    @Test
    @DisplayName("Fetch producer data successfully")
    void testGetProducerData_Success() throws Exception {
        mockSuccessfulTokenFetch();
        mockSuccessfulHttpCall();
        final ProducerConfigDTO expected = createProducerConfig();
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenReturn(expected);

        final ProducerConfigDTO result =
                dataHandler.getProducerData(null);

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(tokenService).fetchToken();
    }

    @Test
    @DisplayName("Fetch consumer data successfully")
    void testGetConsumerData_Success() throws Exception {
        mockSuccessfulTokenFetch();
        mockSuccessfulHttpCall();
        final ConsumerConfigDTO expected = createConsumerConfig();
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenReturn(expected);

        final ConsumerConfigDTO result =
                dataHandler.getConsumerData(null);

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.getClientId());
    }

    @Test
    @DisplayName("Handle HTTP error response")
    void testGetProducerData_HttpError() throws Exception {
        mockSuccessfulTokenFetch();
        when(httpResponse.statusCode()).thenReturn(HTTP_ERROR);
        when(httpClient.send(any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        final ManagementNodeDataException ex = assertThrows(
                ManagementNodeDataException.class,
                () -> dataHandler.getProducerData(null));

        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    @DisplayName("Handle null token")
    void testGetProducerData_NullToken() {
        when(tokenService.fetchToken()).thenReturn(null);

        final ManagementNodeDataException ex = assertThrows(
                ManagementNodeDataException.class,
                () -> dataHandler.getProducerData(null));

        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    @DisplayName("Retry on token verification failure")
    void testTokenRetry() throws Exception {
        when(tokenService.fetchToken())
                .thenReturn(VALID_TOKEN, NEW_TOKEN);
        when(tokenService.verifyToken(VALID_TOKEN))
                .thenReturn(false);
        when(tokenService.verifyToken(NEW_TOKEN))
                .thenReturn(true);
        mockSuccessfulHttpCall();
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenReturn(createProducerConfig());

        dataHandler.getProducerData(null);
        verify(tokenService, times(2)).fetchToken();
    }

    @Test
    @DisplayName("Handle JSON error")
    void testJsonError() throws Exception {
        mockSuccessfulTokenFetch();
        mockSuccessfulHttpCall();
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Parse error") {});

        assertThrows(ManagementNodeDataException.class,
                () -> dataHandler.getProducerData(null));
    }

    @Test
    @DisplayName("Validate null dependencies")
    void testNullDependencies() {
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(
                        null, objectMapper, tokenService));
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(
                        httpClient, null, tokenService));
        assertThrows(NullPointerException.class,
                () -> new ManagementNodeDataHandler(
                        httpClient, objectMapper, null));
    }

    @Test
    @DisplayName("Missing base URL")
    void testMissingBaseUrl() {
        Properties props = createValidProperties();
        props.remove("management.node.base.url");
        propertyUtilMock.when(() ->
                        PropertyUtil.getPropertiesFromAbsoluteFilePath(CONFIG_KEY))
                .thenReturn(props);

        assertThrows(IllegalStateException.class,
                () -> new ManagementNodeDataHandler(
                        httpClient, objectMapper, tokenService));
    }

    @Test
    @DisplayName("Missing producer path")
    void testMissingProducerPath() {
        Properties props = createValidProperties();
        props.remove("management.node.api.endpoints.producer");
        propertyUtilMock.when(() ->
                        PropertyUtil.getPropertiesFromAbsoluteFilePath(CONFIG_KEY))
                .thenReturn(props);

        assertThrows(IllegalStateException.class,
                () -> new ManagementNodeDataHandler(
                        httpClient, objectMapper, tokenService));
    }

    private void mockSuccessfulTokenFetch() {
        when(tokenService.fetchToken()).thenReturn(VALID_TOKEN);
        when(tokenService.verifyToken(VALID_TOKEN))
                .thenReturn(true);
    }

    private void mockSuccessfulHttpCall()
            throws IOException, InterruptedException {
        when(httpResponse.statusCode()).thenReturn(HTTP_OK);
        when(httpResponse.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    private Properties createValidProperties() {
        Properties props = new Properties();
        props.setProperty("management.node.base.url", BASE_URL);
        props.setProperty("management.node.api.endpoints.producer",
                PRODUCER_PATH);
        props.setProperty("management.node.api.endpoints.consumer",
                CONSUMER_PATH);
        props.setProperty("management.node.request.timeout", TIMEOUT);
        return props;
    }

    private ProducerConfigDTO createProducerConfig() {
        final ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }

    private ConsumerConfigDTO createConsumerConfig() {
        final ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }
}