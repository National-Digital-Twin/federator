package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.ConsumerConfiguration;
import uk.gov.dbt.ndtp.federator.model.ProducerConfiguration;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.DataProviderDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.service.FederatorConfigurationService;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * Integration test client using proper DTO structures (ProducerConfigDTO, ConsumerConfigDTO).
 * Tests connectivity and data provider population with correct DTOs.
 *
 * Fixed version with proper SSL handling and client certificate configuration.
 */
public class ConfigurationClientTestWithActualCode {

    public static final Logger LOGGER = LoggerFactory.getLogger("ConfigurationClientTestWithActualCode");


    private static final String KEYCLOAK_TOKEN_URL = "https://localhost:8443/realms/management-node/protocol/openid-connect/token";
    private static final String FEDERATOR_BASE_URL = "https://localhost:8090";
    private static final String CLIENT_ID = "FEDERATOR_BCC";
    private static final String CLIENT_SECRET = "";

    // SSL Configuration
    private static final String TRUSTSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/truststore.jks";
    private static final String TRUSTSTORE_PW = "changeit";
    private static final String KEYSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/keystore.jks";
    private static final String KEYSTORE_PW = "changeit";

    // Set to true to disable SSL verification for testing
    private static final boolean DISABLE_SSL_VERIFICATION = false;

    // Set to true to enable SSL debugging
    private static final boolean ENABLE_SSL_DEBUG = false;

    private final ObjectMapper objectMapper;
    private final Properties config;

    private ManagementNodeDataHandler managementNodeDataHandler;
    private JwtTokenService jwtTokenService;
    private FederatorConfigurationService federatorConfigurationService;
    private InMemoryConfigurationStore configurationStore;

    private String currentJwtToken;

    public ConfigurationClientTestWithActualCode() {
        this.objectMapper = new ObjectMapper();
        this.config = new Properties();

        // Setup SSL before any network operations
        if (DISABLE_SSL_VERIFICATION) {
            LOGGER.info("⚠️  WARNING: SSL verification is disabled!");
            disableSSLVerification();
        } else {
            boolean sslSetup = setupSSLWithValidation();
            if (!sslSetup) {
                LOGGER.error("⚠️  SSL setup failed, attempting to continue...");
            }
        }

        setupConfiguration();
    }

    private void setupConfiguration() {
        // ManagementNodeDataHandler configuration
        config.setProperty("management.node.base.url", FEDERATOR_BASE_URL);
        config.setProperty("management.node.request.timeout", "30");
        config.setProperty("management.node.max.retry.attempts", "3");
        config.setProperty("management.node.retry.delay.seconds", "2");
        config.setProperty("management.node.thread.pool.size", "5");

        // JwtTokenService configuration
        config.setProperty("management.node.jwt.token", "");
        config.setProperty("management.node.jwt.token.refresh.buffer", "300");
        config.setProperty("management.node.jwt.token.auto.refresh.enabled", "true");
        config.setProperty("management.node.jwt.token.file.watching.enabled", "false");

        // FederatorConfigurationService configuration
        config.setProperty("federator.config.cache.enabled", "true");
        config.setProperty("federator.config.auto.refresh.enabled", "false");
        config.setProperty("federator.config.refresh.interval", "3600000");
        config.setProperty("federator.config.cache.expiration", "7200000");
        config.setProperty("federator.config.parallel.refresh.enabled", "true");
    }

    /**
     * Setup SSL with proper client certificate configuration
     */
    private boolean setupSSLWithValidation() {
        try {
            LOGGER.info("\n=== SSL Configuration ===");

            // Load truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            if (!Files.exists(Paths.get(TRUSTSTORE_PATH))) {
                LOGGER.error("⚠️  Truststore not found at: " + TRUSTSTORE_PATH);
                return false;
            }

            try (FileInputStream trustStoreStream = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(trustStoreStream, TRUSTSTORE_PW.toCharArray());
                LOGGER.info("✓ Truststore loaded successfully");
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Load keystore for client certificate (if mutual TLS is required)
            KeyManager[] keyManagers = null;

            if (Files.exists(Paths.get(KEYSTORE_PATH))) {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                try (FileInputStream keyStoreStream = new FileInputStream(KEYSTORE_PATH)) {
                    keyStore.load(keyStoreStream, KEYSTORE_PW.toCharArray());

                    // Debug keystore contents
                    debugKeystore(keyStore);

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(keyStore, KEYSTORE_PW.toCharArray());
                    keyManagers = kmf.getKeyManagers();

                    LOGGER.info("✓ Client certificate keystore loaded successfully");
                }
            } else {
                LOGGER.info("⚠️  Keystore not found at: " + KEYSTORE_PATH);
                LOGGER.info("   Proceeding without client certificate (server may reject if mTLS required)");
            }

            // Create SSL context with both trust and key managers
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, tmf.getTrustManagers(), new SecureRandom());

            // Set as default for all HTTPS connections
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // Also set system properties as fallback
            System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH);
            System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PW);

            if (Files.exists(Paths.get(KEYSTORE_PATH))) {
                System.setProperty("javax.net.ssl.keyStore", KEYSTORE_PATH);
                System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PW);
            }

            LOGGER.info("✓ SSL setup completed successfully!");
            LOGGER.info("========================\n");
            return true;

        } catch (Exception e) {
            LOGGER.error("✗ Failed to setup SSL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Debug keystore contents to verify client certificate
     */
    private void debugKeystore(KeyStore keyStore) {
        try {
            LOGGER.info("\n  Keystore Contents:");
            Enumeration<String> aliases = keyStore.aliases();
            int count = 0;
            while (aliases.hasMoreElements()) {
                count++;
                String alias = aliases.nextElement();
                LOGGER.info("  - Alias: " + alias);

                if (keyStore.isKeyEntry(alias)) {
                    LOGGER.info("    Type: Private Key Entry");
                    if (keyStore.getCertificate(alias) instanceof X509Certificate) {
                        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
                        LOGGER.info("    Subject: " + cert.getSubjectDN());
                        LOGGER.info("    Issuer: " + cert.getIssuerDN());
                        LOGGER.info("    Valid until: " + cert.getNotAfter());
                    }
                } else if (keyStore.isCertificateEntry(alias)) {
                    LOGGER.info("    Type: Certificate Entry");
                }
            }

            if (count == 0) {
                LOGGER.info("  ⚠️  No entries found in keystore!");
            }
        } catch (Exception e) {
            LOGGER.error("  Error reading keystore: " + e.getMessage());
        }
    }

    /**
     * Disable SSL verification for testing purposes only
     */
    private void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            LOGGER.info("✓ SSL verification disabled (TESTING ONLY!)");
        } catch (Exception e) {
            LOGGER.error("Failed to disable SSL verification: " + e.getMessage());
        }
    }

    /**
     * Step 1: Get JWT token from Keycloak
     */
    private String getJWTTokenFromKeycloak() throws Exception {
        LOGGER.info("\n========================================");
        LOGGER.info("STEP 1: Getting JWT Token from Keycloak");
        LOGGER.info("========================================");

        URL url = new URL(KEYCLOAK_TOKEN_URL);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);

            StringBuilder urlParameters = new StringBuilder();
            urlParameters.append("client_id=").append(URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8.toString()));
            urlParameters.append("&grant_type=client_credentials");

            if (CLIENT_SECRET != null && !CLIENT_SECRET.trim().isEmpty()) {
                urlParameters.append("&client_secret=").append(URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8.toString()));
            }

            LOGGER.info("Request URL: " + KEYCLOAK_TOKEN_URL);
            LOGGER.info("Client ID: " + CLIENT_ID);

            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(urlParameters.toString());
                wr.flush();
            }

            int responseCode = connection.getResponseCode();
            LOGGER.info("Response Code: " + responseCode);

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(response.toString());
                String accessToken = jsonResponse.get("access_token").asText();

                LOGGER.info("✓ Successfully retrieved JWT token");
                LOGGER.info("  Token length: " + accessToken.length() + " characters");
                return accessToken;
            } else {
                // Read error response
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                errorReader.close();

                LOGGER.error("✗ Failed to get JWT token");
                LOGGER.error("  Error response: " + errorResponse.toString());
                throw new Exception("Failed to get JWT token. Response Code: " + responseCode);
            }

        } catch (javax.net.ssl.SSLHandshakeException e) {
            LOGGER.error("\n✗ SSL Handshake failed!");
            LOGGER.error("  Error: " + e.getMessage());
            LOGGER.error("\n  Possible causes:");
            LOGGER.error("  1. Server requires client certificate (mTLS) but none provided");
            LOGGER.error("  2. Client certificate not trusted by server");
            LOGGER.error("  3. Certificate expired or invalid");
            LOGGER.error("\n  Solutions:");
            LOGGER.error("  1. Ensure keystore contains valid client certificate");
            LOGGER.error("  2. Set DISABLE_SSL_VERIFICATION = true for testing");
            LOGGER.error("  3. Run with --debug-ssl flag for more details");
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Step 2: Initialize services
     */
    private void initializeServices(String jwtToken) {
        LOGGER.info("\n========================================");
        LOGGER.info("STEP 2: Initializing Services");
        LOGGER.info("========================================");

        this.currentJwtToken = jwtToken;
        config.setProperty("management.node.jwt.token", jwtToken);

        // Initialize services
        this.jwtTokenService = new JwtTokenService(config);
        this.managementNodeDataHandler = new ManagementNodeDataHandler(config);
        this.configurationStore = new InMemoryConfigurationStore(7200000, 50000);
        this.federatorConfigurationService = new FederatorConfigurationService(config);

        LOGGER.info("✓ All services initialized");
    }

    /**
     * Step 3: Test ProducerConfigDTO retrieval with data providers
     */
    private void testProducerConfigDTO() {
        LOGGER.info("\n========================================");
        LOGGER.info("STEP 3: Testing ProducerConfigDTO with DataProviders");
        LOGGER.info("========================================");

        try {
            // Test getting producer configuration response
            ManagementNodeDataHandler.ProducerConfigurationResponse response =
                    managementNodeDataHandler.getProducerConfigurationResponse(currentJwtToken, null);

            if (response != null) {
                LOGGER.info("\n✓ Producer Configuration Response Retrieved");
                LOGGER.info("Client ID: " + response.getClientId());

                if (response.getProducers() != null) {
                    LOGGER.info("Number of Producers: " + response.getProducers().size());

                    for (ProducerDTO producer : response.getProducers()) {
                        LOGGER.info("\n=== Producer Details ===");
                        LOGGER.info("  Name: " + producer.getName());
                        LOGGER.info("  Description: " + producer.getDescription());
                        LOGGER.info("  Host: " + producer.getHost());
                        LOGGER.info("  Port: " + producer.getPort());
                        LOGGER.info("  TLS: " + producer.getTls());
                        LOGGER.info("  IDP Client ID: " + producer.getIdpClientId());
                        LOGGER.info("  Active: " + producer.getActive());

                        // Display DataProviders
                        if (producer.getDataProviders() != null && !producer.getDataProviders().isEmpty()) {
                            LOGGER.info("\n  Data Providers (" + producer.getDataProviders().size() + "):");

                            for (DataProviderDTO dataProvider : producer.getDataProviders()) {
                                LOGGER.info("    ╔══ Data Provider ══╗");
                                LOGGER.info("      Name: " + dataProvider.getName());
                                LOGGER.info("      Topic: " + dataProvider.getTopic());
                                LOGGER.info("      Description: " + dataProvider.getDescription());
                                LOGGER.info("      Active: " + dataProvider.getActive());

                                // Display Consumers for this data provider
                                if (dataProvider.getConsumers() != null && !dataProvider.getConsumers().isEmpty()) {
                                    LOGGER.info("      Consumers (" + dataProvider.getConsumers().size() + "):");
                                    for (ConsumerDTO consumer : dataProvider.getConsumers()) {
                                        LOGGER.info("        • Consumer: " + consumer.getName());
                                        LOGGER.info("          IDP Client ID: " + consumer.getIdpClientId());
                                    }
                                } else {
                                    LOGGER.info("      Consumers: None");
                                }
                            }
                        } else {
                            LOGGER.info("  ⚠️  Data Providers: Empty or null - Check DTO mapping!");
                        }
                    }
                } else {
                    LOGGER.info("⚠️  No producers in response");
                }
            } else {
                LOGGER.info("✗ Failed to get producer configuration response");
            }

        } catch (Exception e) {
            LOGGER.error("✗ ProducerConfigDTO test failed: " + e.getMessage());
        }
    }

    /**
     * Step 4: Test ConsumerConfigDTO retrieval
     */
    private void testConsumerConfigDTO() {
        LOGGER.info("\n========================================");
        LOGGER.info("STEP 4: Testing ConsumerConfigDTO");
        LOGGER.info("========================================");

        try {
            // Test getting consumer configuration response
            ConsumerConfigDTO response =
                    managementNodeDataHandler.getConsumerConfigurationResponse(currentJwtToken, null);

            if (response != null) {
                LOGGER.info("\n✓ Consumer Configuration Response Retrieved");
                LOGGER.info("Client ID: " + response.getClientId());

                // ConsumerConfigDTO contains producers which have data providers with consumers
                if (response.getProducers() != null) {
                    LOGGER.info("Number of Producers in Consumer Response: " + response.getProducers().size());

                    // Extract consumer information from the nested structure
                    int totalConsumers = 0;
                    for (ProducerDTO producer : response.getProducers()) {
                        if (producer.getDataProviders() != null) {
                            for (DataProviderDTO dataProvider : producer.getDataProviders()) {
                                if (dataProvider.getConsumers() != null) {
                                    totalConsumers += dataProvider.getConsumers().size();
                                }
                            }
                        }
                    }
                    LOGGER.info("Total Consumers Found: " + totalConsumers);
                }
            } else {
                LOGGER.info("✗ Failed to get consumer configuration response");
            }

        } catch (Exception e) {
            LOGGER.error("✗ ConsumerConfigDTO test failed: " + e.getMessage());
        }
    }

    /**
     * Step 5: Test service integration with proper DTOs
     */
    private void testServiceIntegration() {
        LOGGER.info("\n========================================");
        LOGGER.info("STEP 5: Testing Service Integration");
        LOGGER.info("========================================");

        try {
            // Test getting all producer configurations
            List<ProducerConfiguration> producers = federatorConfigurationService.getProducerConfigurations();
            LOGGER.info("✓ Retrieved " + producers.size() + " producer configurations via service");

            // Test getting all consumer configurations
            List<ConsumerConfiguration> consumers = federatorConfigurationService.getConsumerConfigurations();
            LOGGER.info("✓ Retrieved " + consumers.size() + " consumer configurations via service");

            // Test cache functionality
            FederatorConfigurationService.FederatorServiceStatistics stats =
                    federatorConfigurationService.getServiceStatistics();
            LOGGER.info("\nService Statistics:");
            LOGGER.info("  Cache Hits: " + stats.getCacheHits());
            LOGGER.info("  Cache Misses: " + stats.getCacheMisses());
            LOGGER.info("  Cache Hit Rate: " + String.format("%.2f%%", stats.getCacheHitRate()));

        } catch (Exception e) {
            LOGGER.error("✗ Service integration test failed: " + e.getMessage());
        }
    }

    /**
     * Step 6: Test direct API endpoints
     */
    private void testDirectAPIEndpoints() {
        LOGGER.info("\n========================================");
        LOGGER.info("STEP 6: Testing Direct API Endpoints");
        LOGGER.info("========================================");

        // Test producer endpoint with empty producer_id parameter
        testEndpoint("/api/v1/configuration/producer?producer_id", "Producer endpoint (null producer_id)");

        // Test consumer endpoint with empty consumer_id parameter
        testEndpoint("/api/v1/configuration/consumer?consumer_id", "Consumer endpoint (null consumer_id)");
    }

    private void testEndpoint(String endpoint, String description) {
        LOGGER.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGGER.info("Testing: " + description);
        LOGGER.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        LOGGER.info("URL: " + FEDERATOR_BASE_URL + endpoint);

        try {
            URL url = new URL(FEDERATOR_BASE_URL + endpoint);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + currentJwtToken);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            LOGGER.info("Response Code: " + responseCode);

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse the JSON response
                com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(response.toString());

                // Print the formatted JSON response
                LOGGER.info("\n📋 API Response (Pretty JSON):");
                LOGGER.info("─────────────────────────────────────────");
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
                LOGGER.info(prettyJson);
                LOGGER.info("─────────────────────────────────────────");

                // Print summary analysis
                LOGGER.info("\n📊 Response Analysis:");
                if (jsonResponse.has("clientId")) {
                    LOGGER.info("  ✓ Client ID: " + jsonResponse.get("clientId").asText());
                }

                if (jsonResponse.has("producers")) {
                    com.fasterxml.jackson.databind.JsonNode producers = jsonResponse.get("producers");
                    LOGGER.info("  ✓ Producers: " + producers.size() + " found");

                    // Analyze structure
                    if (producers.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode firstProducer = producers.get(0);

                        // Check for all expected fields
                        LOGGER.info("\n  Structure validation for first producer:");
                        LOGGER.info("    " + (firstProducer.has("name") ? "✓" : "✗") + " name field");
                        LOGGER.info("    " + (firstProducer.has("description") ? "✓" : "✗") + " description field");
                        LOGGER.info("    " + (firstProducer.has("host") ? "✓" : "✗") + " host field");
                        LOGGER.info("    " + (firstProducer.has("port") ? "✓" : "✗") + " port field");
                        LOGGER.info("    " + (firstProducer.has("tls") ? "✓" : "✗") + " tls field");
                        LOGGER.info("    " + (firstProducer.has("active") ? "✓" : "✗") + " active field");
                        LOGGER.info("    " + (firstProducer.has("idpClientId") ? "✓" : "✗") + " idpClientId field");

                        if (firstProducer.has("dataProviders")) {
                            com.fasterxml.jackson.databind.JsonNode dataProviders = firstProducer.get("dataProviders");
                            LOGGER.info("    ✓ dataProviders field (" + dataProviders.size() + " items)");

                            if (dataProviders.size() > 0) {
                                com.fasterxml.jackson.databind.JsonNode firstDataProvider = dataProviders.get(0);
                                LOGGER.info("\n  Data Provider structure validation:");
                                LOGGER.info("    " + (firstDataProvider.has("name") ? "✓" : "✗") + " name field");
                                LOGGER.info("    " + (firstDataProvider.has("topic") ? "✓" : "✗") + " topic field");
                                LOGGER.info("    " + (firstDataProvider.has("consumers") ? "✓" : "✗") + " consumers field");
                            }
                        } else {
                            LOGGER.info("    ✗ dataProviders field missing!");
                        }
                    }
                }
            } else {
                // Read error response
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                errorReader.close();

                LOGGER.info("\n✗ Request failed with code: " + responseCode);
                LOGGER.info("Error response: " + errorResponse.toString());
            }

            connection.disconnect();
        } catch (Exception e) {
            LOGGER.info("✗ Error: " + e.getMessage());
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        LOGGER.info("\n========================================");
        LOGGER.info("Cleaning Up Resources");
        LOGGER.info("========================================");

        if (federatorConfigurationService != null) {
            try {
                federatorConfigurationService.close();
                LOGGER.info("✓ FederatorConfigurationService closed");
            } catch (Exception e) {
                LOGGER.info("⚠️  Error closing FederatorConfigurationService: " + e.getMessage());
            }
        }

        if (managementNodeDataHandler != null) {
            try {
                managementNodeDataHandler.close();
                LOGGER.info("✓ ManagementNodeDataHandler closed");
            } catch (Exception e) {
                LOGGER.info("⚠️  Error closing ManagementNodeDataHandler: " + e.getMessage());
            }
        }

        if (jwtTokenService != null) {
            try {
                jwtTokenService.close();
                LOGGER.info("✓ JwtTokenService closed");
            } catch (Exception e) {
                LOGGER.info("⚠️  Error closing JwtTokenService: " + e.getMessage());
            }
        }
    }

    /**
     * Run the complete integration test
     */
    public void runTest() {
        LOGGER.info("\n=====================================");
        LOGGER.info("CONFIGURATION DTO INTEGRATION TEST");
        LOGGER.info("Testing with ProducerConfigDTO and ConsumerConfigDTO");
        LOGGER.info("=====================================");

        long startTime = System.currentTimeMillis();
        boolean testPassed = false;

        try {
            // Step 1: Get JWT token from Keycloak
            String jwtToken = getJWTTokenFromKeycloak();

            // Step 2: Initialize all services
            initializeServices(jwtToken);

            // Step 3: Test ProducerConfigDTO with DataProviders
            testProducerConfigDTO();

            // Step 4: Test ConsumerConfigDTO
            testConsumerConfigDTO();

            // Step 5: Test service integration
            testServiceIntegration();

            // Step 6: Test direct API endpoints
            testDirectAPIEndpoints();

            testPassed = true;

        } catch (javax.net.ssl.SSLException e) {
            LOGGER.error("\n✗✗✗ SSL ERROR ✗✗✗");
            LOGGER.error("Error: " + e.getMessage());
            LOGGER.error("\nTry one of these solutions:");
            LOGGER.error("1. Set DISABLE_SSL_VERIFICATION = true (for testing only)");
            LOGGER.error("2. Ensure client certificate is in keystore: " + KEYSTORE_PATH);
            LOGGER.error("3. Run with --debug-ssl flag for detailed SSL debugging");
        } catch (Exception e) {
            LOGGER.error("\n✗✗✗ TEST FAILED ✗✗✗");
            LOGGER.error("Error: " + e.getMessage());
        } finally {
            cleanup();
        }

        long duration = System.currentTimeMillis() - startTime;

        LOGGER.info("\n=====================================");
        LOGGER.info("TEST SUMMARY");
        LOGGER.info("=====================================");
        LOGGER.info("Result: " + (testPassed ? "✓✓✓ PASSED" : "✗✗✗ FAILED"));
        LOGGER.info("Execution Time: " + duration + " ms");
        LOGGER.info("\nConfiguration:");
        LOGGER.info("- SSL Verification: " + (DISABLE_SSL_VERIFICATION ? "DISABLED" : "ENABLED"));
        LOGGER.info("- Keystore: " + KEYSTORE_PATH);
        LOGGER.info("- Truststore: " + TRUSTSTORE_PATH);
        LOGGER.info("\nKey Points:");
        LOGGER.info("- Using ProducerConfigDTO and ConsumerConfigDTO");
        LOGGER.info("- DataProviders should use DataProviderDTO");
        LOGGER.info("- Producer/Consumer IDs passed as null");
        LOGGER.info("\nEndpoints Tested:");
        LOGGER.info("- " + KEYCLOAK_TOKEN_URL);
        LOGGER.info("- " + FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id");
        LOGGER.info("- " + FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id");
        LOGGER.info("=====================================");

        System.exit(testPassed ? 0 : 1);
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        LOGGER.info("Starting Configuration DTO Integration Test...");
        LOGGER.info("Java Version: " + System.getProperty("java.version"));
        LOGGER.info("Java Vendor: " + System.getProperty("java.vendor"));

        boolean debugSSL = false;
        boolean showHelp = false;

        // Parse command line arguments
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                showHelp = true;
            } else if (arg.equals("--debug-ssl")) {
                debugSSL = true;
            }
        }

        if (showHelp) {
            LOGGER.info("\nUsage: java ConfigurationClientTestWithActualCode [options]");
            LOGGER.info("Options:");
            LOGGER.info("  --help, -h       Show this help message");
            LOGGER.info("  --debug-ssl      Enable SSL debugging");
            LOGGER.info("\nThis test verifies:");
            LOGGER.info("  1. ProducerConfigDTO structure with DataProviderDTO");
            LOGGER.info("  2. ConsumerConfigDTO structure");
            LOGGER.info("  3. DataProvider population in responses");
            LOGGER.info("  4. Service integration with proper DTOs");
            LOGGER.info("\nSSL Configuration:");
            LOGGER.info("  - Set DISABLE_SSL_VERIFICATION = true to bypass SSL checks (testing only)");
            LOGGER.info("  - Ensure keystore contains client certificate for mTLS");
            LOGGER.info("  - Use --debug-ssl to troubleshoot SSL handshake issues");
            return;
        }

        if (debugSSL || ENABLE_SSL_DEBUG) {
            System.setProperty("javax.net.debug", "ssl,handshake,trustmanager");
            LOGGER.info("SSL debugging enabled");
        }

        ConfigurationClientTestWithActualCode test = new ConfigurationClientTestWithActualCode();
        test.runTest();
    }
}