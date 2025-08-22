package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;

/**
 * Test client to verify connectivity between management-node and federator.
 *
 * @author Rakesh Chiluka
 * @version 3.0
 * @since 2025-01-20
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
    private static final String STORE_PW = "changeit";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ManagementNodeDataHandler dataHandler;
    private String cachedToken;

    /**
     * Initializes the test client with required components.
     */
    public ConfigurationClientTest() {
        this.objectMapper = createObjectMapper();
        this.httpClient = createHttpClient();
        this.dataHandler = createDataHandler();
    }

    /**
     * Creates and configures ObjectMapper.
     */
    private ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * Creates ManagementNodeDataHandler with custom token service.
     */
    private ManagementNodeDataHandler createDataHandler() {
        // Create a custom token service that uses our cached token
        JwtTokenService tokenService = new JwtTokenService(objectMapper) {
            @Override
            public String fetchJwtToken() throws IOException {
                if (cachedToken == null) {
                    throw new IOException("Token not available. Call fetchToken() first.");
                }
                return cachedToken;
            }
        };

        return new ManagementNodeDataHandler(
                httpClient, objectMapper, MANAGEMENT_NODE_URL,
                tokenService, TIMEOUT);
    }

    /**
     * Fetches JWT token using the configured HTTP client.
     */
    public void fetchToken() throws Exception {
        log.info("Fetching JWT token from Keycloak...");
        final String clientSecret = System.getenv("CLIENT_SECRET");

        final String formData = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                "&grant_type=client_credentials" +
                (clientSecret != null ? "&client_secret=" +
                        URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) : "");

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK_URL + TOKEN_ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        final HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            this.cachedToken = objectMapper.readTree(response.body())
                    .get("access_token").asText();
            log.info("Token fetched successfully");
        } else {
            throw new RuntimeException("Failed to fetch token. Status: " + response.statusCode());
        }
    }

    /**
     * Alternative: Use reflection to call old method signature if available.
     */
    private void callWithToken(String methodName) throws Exception {
        try {
            // Try to call the old method signature with token parameter
            Method method = dataHandler.getClass().getMethod(methodName,
                    String.class, String.class);
            method.invoke(dataHandler, cachedToken, null);
        } catch (NoSuchMethodException e) {
            // Fall back to new signature without parameters
            Method method = dataHandler.getClass().getMethod(methodName);
            method.invoke(dataHandler);
        }
    }

    /**
     * Retrieves producer configuration.
     */
    public void getProducerConfig() throws Exception {
        log.info("Fetching producer configuration...");

        // Ensure token is available
        if (cachedToken == null) {
            fetchToken();
        }

        final ProducerConfigDTO config = dataHandler.getProducerData();
        log.info("Producer Config - Client ID: {}, Producers: {}",
                config.getClientId(),
                config.getProducers() != null ? config.getProducers().size() : 0);
    }

    /**
     * Retrieves consumer configuration.
     */
    public void getConsumerConfig() throws Exception {
        log.info("Fetching consumer configuration...");

        // Ensure token is available
        if (cachedToken == null) {
            fetchToken();
        }

        final ConsumerConfigDTO config = dataHandler.getConsumerData();
        log.info("Consumer Config - Client ID: {}, Producers: {}",
                config.getClientId(),
                config.getProducers() != null ? config.getProducers().size() : 0);
    }

    /**
     * Creates HTTP client with SSL configuration.
     */
    private HttpClient createHttpClient() {
        try {
            final SSLContext sslContext = createSSLContext();
            log.info("SSL configured with keystore: {} and truststore: {}",
                    KEYSTORE_PATH, TRUSTSTORE_PATH);
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(TIMEOUT)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP client with SSL", e);
        }
    }

    /**
     * Creates SSL context with keystore and truststore.
     */
    private SSLContext createSSLContext() throws Exception {
        final KeyStore keyStore = loadKeyStore(KEYSTORE_PATH, STORE_PW);
        final KeyStore trustStore = loadKeyStore(TRUSTSTORE_PATH, STORE_PW);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, STORE_PW.toCharArray());

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    /**
     * Loads keystore from file.
     */
    private KeyStore loadKeyStore(final String path, final String password)
            throws Exception {
        final KeyStore store = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(path)) {
            store.load(fis, password.toCharArray());
        }
        return store;
    }

    /**
     * Tests connectivity to management node.
     */
    public boolean testConnectivity() {
        log.info("Testing connectivity to management node...");
        final boolean connected = dataHandler.testConnectivity();
        log.info("{} Management node is {}",
                connected ? "✓" : "✗",
                connected ? "reachable" : "not reachable");
        return connected;
    }

    /**
     * Main method to execute configuration tests.
     */
    public static void main(final String[] args) {
        try {
            final ConfigurationClientTest test = new ConfigurationClientTest();

            // Fetch token first using our SSL-configured client
            test.fetchToken();

            // Test connectivity
            if (!test.testConnectivity()) {
                log.error("✗✗✗ Cannot reach management node");
                System.exit(1);
            }

            // Run tests
            test.getProducerConfig();
            test.getConsumerConfig();

            log.info("✓✓✓ ALL TESTS PASSED");
        } catch (Exception e) {
            log.error("✗✗✗ TEST FAILED: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}