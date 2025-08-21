package uk.gov.dbt.ndtp.federator.connectivity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;

/**
 * To check the connectivity between management-node and federator
 * by passing JWT token to retrieve the configuration details from the producer and consumer endpoints.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@Slf4j
public class ConfigurationClientTest {

    private static final String KEYCLOAK_URL = "https://localhost:8443";
    private static final String MANAGEMENT_NODE_URL = "https://localhost:8090";
    private static final String TOKEN_ENDPOINT = "/realms/management-node/protocol/openid-connect/token";
    private static final String CLIENT_ID = "FEDERATOR_BCC";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String KEYSTORE_PATH = "/home/vagrant/opt/certs/keystore.jks";
    private static final String TRUSTSTORE_PATH = "/home/vagrant/opt/certs/truststore.jks";
    private static final String STORE_PW = "changeit"; // Update with actual password

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ManagementNodeDataHandler dataHandler;

    /**
     * Initializes the test client with required components.
     */
    public ConfigurationClientTest() {
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.httpClient = createHttpClient();
        this.dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, MANAGEMENT_NODE_URL,
                new JwtTokenService(objectMapper), TIMEOUT);
    }

    /**
     * Fetches JWT token from Keycloak using client credentials.
     *
     * @param clientSecret the client secret for authentication
     * @return JWT access token
     * @throws Exception if token retrieval fails
     */
    public String fetchJwtToken(final String clientSecret) throws Exception {
        log.info("Fetching JWT token from Keycloak...");
        final String formData = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                "&grant_type=client_credentials" +
                (clientSecret != null ? "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) : "");

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK_URL + TOKEN_ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            final String token = objectMapper.readTree(response.body()).get("access_token").asText();
            log.info("Token fetched successfully");
            return token;
        }
        throw new RuntimeException("Failed to fetch token. Status: " + response.statusCode());
    }

    /**
     * Retrieves producer configuration from Management Node.
     * @param producerId  it's optional for now will be used in the future implementation .
     * @param jwtToken the JWT token for authentication
     * @throws Exception if retrieval fails
     */
    public void getProducerConfig(final String jwtToken, final String producerId) throws Exception {
        log.info("Fetching producer configuration...");
        final ProducerConfigDTO config = dataHandler.getProducerData(jwtToken, null);
        log.info("Producer Config - Client ID: {}, Producers: {}",
                config.getClientId(),
                config.getProducers() != null ? config.getProducers().size() : 0);
        if (log.isDebugEnabled()) {
            log.debug("Producer JSON: {}", objectMapper.writeValueAsString(config));
        }
    }

    /**
     * Retrieves consumer configuration from Management Node.
     *
     * @param jwtToken the JWT token for authentication
     * @param consumerId it's optional for now will be used in the future implementation .
     * @throws Exception if retrieval fails
     */
    public void getConsumerConfig(final String jwtToken, final String consumerId) throws Exception {
        log.info("Fetching consumer configuration...");
        final ConsumerConfigDTO config = dataHandler.getConsumerData(jwtToken, null);
        log.info("Consumer Config - Client ID: {}, Producers: {}",
                config.getClientId(),
                config.getProducers() != null ? config.getProducers().size() : 0);
        if (log.isDebugEnabled()) {
            log.debug("Consumer JSON: {}", objectMapper.writeValueAsString(config));
        }
    }

    /**
     * Creates HTTP client with SSL configuration using local keystore and truststore.
     *
     * @return configured HttpClient
     */
    private HttpClient createHttpClient() {
        try {
            final KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(KEYSTORE_PATH), STORE_PW.toCharArray());
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, STORE_PW.toCharArray());

            final KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(new FileInputStream(TRUSTSTORE_PATH), STORE_PW.toCharArray());
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            log.info("SSL configured with keystore: {} and truststore: {}", KEYSTORE_PATH, TRUSTSTORE_PATH);
            return HttpClient.newBuilder().sslContext(sslContext).connectTimeout(TIMEOUT).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP client with SSL", e);
        }
    }

    /**
     * Main method to execute configuration tests.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        try {
            final ConfigurationClientTest test = new ConfigurationClientTest();
            final String clientSecret = System.getenv("CLIENT_SECRET");
            final String jwtToken = test.fetchJwtToken(clientSecret);
            test.getProducerConfig(jwtToken, null);
            test.getConsumerConfig(jwtToken, null);
            log.info("✓✓✓ ALL TESTS PASSED");
        } catch (Exception e) {
            log.error("✗✗✗ TEST FAILED: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}