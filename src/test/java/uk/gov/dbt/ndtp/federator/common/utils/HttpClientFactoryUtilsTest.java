/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorSslException;

class HttpClientFactoryUtilsTest {

    @Test
    void createHttpClientWithMtls_success() throws Exception {
        Properties props = new Properties();
        props.setProperty("idp.keystore.path", "ks");
        props.setProperty("idp.keystore.password", "ksp");
        props.setProperty("idp.truststore.path", "ts");
        props.setProperty("idp.truststore.password", "tsp");

        try (MockedStatic<SSLUtils> sslUtilsMock = mockStatic(SSLUtils.class)) {
            sslUtilsMock
                    .when(() -> SSLUtils.createSSLContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(SSLContext.getDefault());

            HttpClient client = HttpClientFactoryUtils.createHttpClientWithMtls(props);
            assertNotNull(client);
        }
    }

    @Test
    void createHttpClientWithMtls_failure() {
        Properties props = new Properties();
        try (MockedStatic<SSLUtils> sslUtilsMock = mockStatic(SSLUtils.class)) {
            sslUtilsMock
                    .when(() -> SSLUtils.createSSLContext(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("SSL Error"));

            assertThrows(FederatorSslException.class, () -> HttpClientFactoryUtils.createHttpClientWithMtls(props));
        }
    }

    @Test
    void createHttpClient_success() throws Exception {
        Properties props = new Properties();
        props.setProperty("idp.truststore.path", "ts");
        props.setProperty("idp.truststore.password", "tsp");

        try (MockedStatic<SSLUtils> sslUtilsMock = mockStatic(SSLUtils.class)) {
            sslUtilsMock
                    .when(() -> SSLUtils.createSSLContextWithTrustStore(anyString(), anyString()))
                    .thenReturn(SSLContext.getDefault());

            HttpClient client = HttpClientFactoryUtils.createHttpClient(props);
            assertNotNull(client);
        }
    }

    @Test
    void createHttpClient_failure() {
        Properties props = new Properties();
        try (MockedStatic<SSLUtils> sslUtilsMock = mockStatic(SSLUtils.class)) {
            sslUtilsMock
                    .when(() -> SSLUtils.createSSLContextWithTrustStore(any(), any()))
                    .thenThrow(new RuntimeException("SSL Error"));

            assertThrows(FederatorSslException.class, () -> HttpClientFactoryUtils.createHttpClient(props));
        }
    }

    @Test
    void constructor_isPrivate() throws Exception {
        java.lang.reflect.Constructor<HttpClientFactoryUtils> constructor =
                HttpClientFactoryUtils.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
    }
}
