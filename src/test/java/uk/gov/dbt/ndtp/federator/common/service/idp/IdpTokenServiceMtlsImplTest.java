/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.idp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.common.utils.ResilienceSupport;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

class IdpTokenServiceMtlsImplTest {

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    private MockedStatic<RedisUtil> redisUtilMockedStatic;
    private RedisUtil redisUtil;
    private Properties properties;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        objectMapper = mock(ObjectMapper.class);
        propertyUtilMockedStatic = mockStatic(PropertyUtil.class);
        redisUtilMockedStatic = mockStatic(RedisUtil.class);
        redisUtil = mock(RedisUtil.class);
        redisUtilMockedStatic.when(RedisUtil::getInstance).thenReturn(redisUtil);

        properties = new Properties();
        properties.setProperty("idp.jwks.url", "http://localhost/jwks");
        properties.setProperty("idp.token.url", "http://localhost/token");
        properties.setProperty("idp.client.id", "test-client-id");
        propertyUtilMockedStatic
                .when(() -> PropertyUtil.getPropertiesFromFilePath(anyString()))
                .thenReturn(properties);
        propertyUtilMockedStatic
                .when(() -> PropertyUtil.getPropertyIntValue(anyString(), anyString()))
                .thenReturn(3);
        propertyUtilMockedStatic
                .when(() -> PropertyUtil.getPropertyDurationValue(anyString(), anyString()))
                .thenReturn(java.time.Duration.ofMinutes(1));
        propertyUtilMockedStatic
                .when(() -> PropertyUtil.getPropertyValue(anyString(), anyString()))
                .thenReturn("java.lang.RuntimeException");

        ResilienceSupport.clearForTests();
    }

    @AfterEach
    void tearDown() {
        propertyUtilMockedStatic.close();
        redisUtilMockedStatic.close();
    }

    @Test
    void fetchToken_returnsCachedToken() {
        when(redisUtil.getValue(anyString(), eq(String.class), eq(true))).thenReturn("cached-token");

        IdpTokenServiceMtlsImpl service = new IdpTokenServiceMtlsImpl(httpClient, objectMapper);
        String token = service.fetchToken("node-1");

        assertEquals("cached-token", token);
        verifyNoInteractions(httpClient);
    }

    @Test
    void fetchToken_fetchesFromIdpWhenNotCached() throws Exception {
        when(redisUtil.getValue(anyString(), eq(String.class), eq(true))).thenReturn(null);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\": \"new-token\", \"expires_in\": 3600}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("access_token", "new-token", "expires_in", 3600));

        IdpTokenServiceMtlsImpl service = new IdpTokenServiceMtlsImpl(httpClient, objectMapper);
        String token = service.fetchToken("node-1");

        assertEquals("new-token", token);
        verify(redisUtil).setValue(("management_node_node-1_access_token"), ("new-token"), (3600L));
    }

    @Test
    void fetchToken_throwsExceptionOnIdpError() throws Exception {
        when(redisUtil.getValue(anyString(), eq(String.class), eq(true))).thenReturn(null);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("error");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        IdpTokenServiceMtlsImpl service = new IdpTokenServiceMtlsImpl(httpClient, objectMapper);
        assertThrows(FederatorTokenException.class, () -> service.fetchToken("node-1"));
    }

    @Test
    void fetchToken_defaultNodeId() throws Exception {
        when(redisUtil.getValue(anyString(), eq(String.class), eq(true))).thenReturn(null);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\": \"default-token\", \"expires_in\": 3600}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("access_token", "default-token", "expires_in", 3600));

        IdpTokenServiceMtlsImpl service = new IdpTokenServiceMtlsImpl(httpClient, objectMapper);
        String token = service.fetchToken(null);

        assertEquals("default-token", token);
        verify(redisUtil).setValue("management_node_default_access_token", "default-token", 3600L);
    }

    @Test
    void fetchTokenNoArgs() {
        // This calls fetchTokenWithResilience which is not easily mockable without more effort,
        // but we can try to see if it calls fetchToken(null)
        // Actually fetchTokenWithResilience in AbstractIdpTokenService calls fetchToken(null)

        when(redisUtil.getValue(anyString(), eq(String.class), eq(true))).thenReturn("resilient-token");
        IdpTokenServiceMtlsImpl service = new IdpTokenServiceMtlsImpl(httpClient, objectMapper);
        String token = service.fetchToken();
        assertEquals("resilient-token", token);
    }
}
