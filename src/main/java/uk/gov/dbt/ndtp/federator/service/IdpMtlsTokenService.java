// src/main/java/uk/gov/dbt/ndtp/federator/service/IdpMtlsTokenService.java
package uk.gov.dbt.ndtp.federator.service;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

@Slf4j
public class IdpMtlsTokenService {

  private final String keycloakServerUrl;
  private final String realm;
  private final String clientId;
  private final String keystorePath;
  private final String keystorePassword;
  private final String truststorePath;
  private final String truststorePassword;

  public IdpMtlsTokenService() {
    this.keycloakServerUrl = PropertyUtil.getPropertyValue("keycloak.server.url");
    this.realm = PropertyUtil.getPropertyValue("keycloak.realm");
    this.clientId = PropertyUtil.getPropertyValue("keycloak.client.id");
    this.keystorePath = PropertyUtil.getPropertyValue("keystore.path");
    this.keystorePassword = PropertyUtil.getPropertyValue("keystore.password");
    this.truststorePath = PropertyUtil.getPropertyValue("truststore.path");
    this.truststorePassword = PropertyUtil.getPropertyValue("truststore.password");
  }

  // For testing only
  public static void main(String[] args) {
    // From PropertyUtil read idp.properties  and initiate IdpMtlsTokenService
    PropertyUtil.init(new File(args[0]));
    System.out.println(new IdpMtlsTokenService().getAccessToken());
  }

  public String getAccessToken() {
    try {
      SSLContext sslContext = createSSLContext();
      try (Keycloak keycloak = KeycloakBuilder.builder()
          .serverUrl(keycloakServerUrl)
          .realm(realm)
          .clientId(clientId)
          .resteasyClient(ResteasyClientBuilder.newBuilder()
              .sslContext(sslContext)
              .build())
          .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
          .build()) {
        String token = keycloak.tokenManager().getAccessTokenString();
        log.debug("Successfully retrieved access token:{}", token);
        return token;
      }
    } catch (Exception e) {
      log.error("Error during mTLS authentication", e);
      throw new RuntimeException("Failed to get access token", e);
    }
  }

  private void printCertificate(KeyStore keyStore, String aliasFrom) throws KeyStoreException {
    Enumeration<String> aliases = keyStore.aliases();
    while (aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      log.info(aliasFrom, alias);
      try {
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
        log.info("Certificate for alias {} : {} ", alias, cert.getSubjectDN());
      } catch (Exception e) {
        log.error("Error retrieving certificate for alias {} {}", alias, e.getMessage());
      }
    }
  }

  protected SSLContext createSSLContext() throws Exception {
    // Load client keystore (PKCS12)
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream is = getClass().getResourceAsStream(keystorePath)) {
      keyStore.load(is, keystorePassword.toCharArray());
    }
    printCertificate(keyStore, "Keystore alias: ");

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, keystorePassword.toCharArray());

    // Load truststore (JKS)
    KeyStore trustStore = KeyStore.getInstance("JKS");
    try (InputStream is = getClass().getResourceAsStream(truststorePath)) {
      trustStore.load(is, truststorePassword.toCharArray());
    }
    printCertificate(trustStore, "Truststore alias: ");

    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    return sslContext;
  }
}