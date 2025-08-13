// src/test/java/uk/gov/dbt/ndtp/federator/service/IdpMtlsTokenServiceTest.java
package uk.gov.dbt.ndtp.federator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.token.TokenManager;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

@ExtendWith(MockitoExtension.class)
class IdpMtlsTokenServiceTest {

  @BeforeEach
  void setUp() {
    // No-op, all mocking is done in test method
  }

  // Java
  @Test
  void testGetAccessToken() throws Exception {
    try (
        MockedStatic<PropertyUtil> mockedPropertyUtil = Mockito.mockStatic(PropertyUtil.class);
        MockedStatic<KeycloakBuilder> mockedKeycloakBuilder = Mockito.mockStatic(
            KeycloakBuilder.class)
    ) {
      // Mock property values
      mockedPropertyUtil.when(() -> PropertyUtil.getPropertyValue("keycloak.server.url"))
          .thenReturn("https://test-keycloak.com");
      mockedPropertyUtil.when(() -> PropertyUtil.getPropertyValue("keycloak.realm"))
          .thenReturn("test-realm");
      mockedPropertyUtil.when(() -> PropertyUtil.getPropertyValue("keycloak.client.id"))
          .thenReturn("test-client");
      mockedPropertyUtil.when(() -> PropertyUtil.getPropertyValue("keystore.path"))
          .thenReturn("/test-client.p12");
      mockedPropertyUtil.when(() -> PropertyUtil.getPropertyValue("keystore.password"))
          .thenReturn("test-password");
      mockedPropertyUtil.when(() -> PropertyUtil.getPropertyValue("truststore.path"))
          .thenReturn("/test-truststore.jks");
      mockedPropertyUtil.when(() -> PropertyUtil.getPropertyValue("truststore.password"))
          .thenReturn("test-trust-password");

      // Mock KeycloakBuilder and Keycloak
      KeycloakBuilder builderMock = mock(KeycloakBuilder.class, RETURNS_SELF);
      mockedKeycloakBuilder.when(KeycloakBuilder::builder).thenReturn(builderMock);

      Keycloak keycloakMock = mock(Keycloak.class);
      when(builderMock.build()).thenReturn(keycloakMock);

      TokenManager tokenManagerMock = mock(TokenManager.class);
      when(keycloakMock.tokenManager()).thenReturn(tokenManagerMock);
      when(tokenManagerMock.getAccessTokenString()).thenReturn("mock-token");

      // Use a named variable for the SSLContext mock
      SSLContext sslContextMock = SSLContext.getInstance("TLS");
      sslContextMock.init(null, new TrustManager[]{
          new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
              return null;
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs,
                String authType) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
                String authType) {
            }
          }
      }, new java.security.SecureRandom());

      // Spy and mock createSSLContext
      IdpMtlsTokenService service = spy(new IdpMtlsTokenService());
      doReturn(sslContextMock).when(service).createSSLContext();

      // Call the method under test
      String token = service.getAccessToken();
      assertEquals("mock-token", token);
    }
  }
}