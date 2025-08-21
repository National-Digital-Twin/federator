package uk.gov.dbt.ndtp.federator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for Management Node connectivity and configuration retrieval.
 *
 * <p>These tests require the following environment variables:
 * <ul>
 *   <li>RUN_INTEGRATION_TESTS=true - Enable integration tests</li>
 *   <li>TRUSTSTORE_PW - Password for the truststore</li>
 *   <li>JWT_TOKEN - JWT token for authentication</li>
 * </ul>
 *
 * <p>Tests will be skipped if RUN_INTEGRATION_TESTS is not set to 'true'.
 *
 * @author National Digital Twin Team
 * @version 1.0
 * @since 2025-01-20
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "false") // Change to "true" to enable tests
class ManagementNodeIntegrationTest {

    private static final String MANAGEMENT_NODE_URL = "https://localhost:8090";
    private static final String TRUSTSTORE_PATH = "/home/vagrant/opt/certs/truststore.jks";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String ENV_TRUSTSTORE_SECRET = "TRUSTSTORE_SECRET";
    private static final String ENV_JWT_TOKEN = "JWT_TOKEN";

    private ManagementNodeDataHandler dataHandler;
    private String jwtToken;

    /**
     * Sets up test dependencies before each test.
     *
     * @throws Exception if setup fails
     */
    @BeforeEach
    void setUp() throws Exception {
        // Skip if environment is not properly configured
        assumeTrue(isEnvironmentConfigured(),
                "Skipping test: Required environment variables not set");

        final HttpClient httpClient = createHttpClient();
        final ObjectMapper objectMapper = new ObjectMapper();
        final JwtTokenService tokenService = new JwtTokenService(objectMapper);

        this.dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, MANAGEMENT_NODE_URL, tokenService, TIMEOUT);
        this.jwtToken = System.getenv(ENV_JWT_TOKEN);
    }

    /**
     * Tests connectivity to Management Node.
     */
    @Test
    @DisplayName("Should successfully connect to Management Node")
    void testManagementNodeConnectivity() {
        final boolean isConnected = dataHandler.testConnectivity();
        assertTrue(isConnected, "Failed to connect to Management Node");
    }

    /**
     * Tests retrieval of producer configuration.
     *
     * @throws Exception if test fails
     */
    @Test
    @DisplayName("Should retrieve producer configuration with valid JWT")
    void testGetProducerConfiguration() throws Exception {
        final ProducerConfigDTO config = dataHandler.getProducerData(jwtToken, null);

        assertNotNull(config, "Producer configuration should not be null");
        assertNotNull(config.getClientId(), "Client ID should not be null");
        assertNotNull(config.getProducers(), "Producers list should not be null");
    }

    /**
     * Tests retrieval of consumer configuration.
     *
     * @throws Exception if test fails
     */
    @Test
    @DisplayName("Should retrieve consumer configuration with valid JWT")
    void testGetConsumerConfiguration() throws Exception {
        final ConsumerConfigDTO config = dataHandler.getConsumerData(jwtToken, null);

        assertNotNull(config, "Consumer configuration should not be null");
        assertNotNull(config.getClientId(), "Client ID should not be null");
        assertNotNull(config.getProducers(), "Producers list should not be null");
    }

    /**
     * Creates HTTP client with SSL configuration.
     *
     * @return configured HttpClient
     * @throws Exception if client creation fails
     */
    private HttpClient createHttpClient() throws Exception {
        final String truststoreSecret = System.getenv(ENV_TRUSTSTORE_SECRET);
        final KeyStore trustStore = KeyStore.getInstance("JKS");

        try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
            trustStore.load(fis, truststoreSecret.toCharArray());
        }

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Checks if required environment variables are configured.
     *
     * @return true if all required variables are set, false otherwise
     */
    private boolean isEnvironmentConfigured() {
        final String truststorePw = System.getenv(ENV_TRUSTSTORE_SECRET);
        final String token = System.getenv(ENV_JWT_TOKEN);

        return truststorePw != null && !truststorePw.trim().isEmpty()
                && token != null && !token.trim().isEmpty();
    }
}