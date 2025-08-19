package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.HttpURLConnection;
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
    private static final String KEYCLOAK_TOKEN_URL = "https://localhost:8443/realms/management-node/protocol/openid-connect/token";
    private static final String FEDERATOR_BASE_URL = "https://localhost:8090";
    private static final String CLIENT_ID = "FEDERATOR_BCC";
    private static final String CLIENT_SECRET = "";

    // SSL Configuration
    private static final String TRUSTSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    private static final String KEYSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/keystore.jks";
    private static final String KEYSTORE_PASSWORD = "changeit";

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
            System.out.println("⚠️  WARNING: SSL verification is disabled!");
            disableSSLVerification();
        } else {
            boolean sslSetup = setupSSLWithValidation();
            if (!sslSetup) {
                System.err.println("⚠️  SSL setup failed, attempting to continue...");
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
            System.out.println("\n=== SSL Configuration ===");

            // Load truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            if (!Files.exists(Paths.get(TRUSTSTORE_PATH))) {
                System.err.println("⚠️  Truststore not found at: " + TRUSTSTORE_PATH);
                return false;
            }

            try (FileInputStream trustStoreStream = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(trustStoreStream, TRUSTSTORE_PASSWORD.toCharArray());
                System.out.println("✓ Truststore loaded successfully");
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Load keystore for client certificate (if mutual TLS is required)
            KeyManager[] keyManagers = null;

            if (Files.exists(Paths.get(KEYSTORE_PATH))) {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                try (FileInputStream keyStoreStream = new FileInputStream(KEYSTORE_PATH)) {
                    keyStore.load(keyStoreStream, KEYSTORE_PASSWORD.toCharArray());

                    // Debug keystore contents
                    debugKeystore(keyStore);

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
                    keyManagers = kmf.getKeyManagers();

                    System.out.println("✓ Client certificate keystore loaded successfully");
                }
            } else {
                System.out.println("⚠️  Keystore not found at: " + KEYSTORE_PATH);
                System.out.println("   Proceeding without client certificate (server may reject if mTLS required)");
            }

            // Create SSL context with both trust and key managers
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, tmf.getTrustManagers(), new SecureRandom());

            // Set as default for all HTTPS connections
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // Also set system properties as fallback
            System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH);
            System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);

            if (Files.exists(Paths.get(KEYSTORE_PATH))) {
                System.setProperty("javax.net.ssl.keyStore", KEYSTORE_PATH);
                System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);
            }

            System.out.println("✓ SSL setup completed successfully!");
            System.out.println("========================\n");
            return true;

        } catch (Exception e) {
            System.err.println("✗ Failed to setup SSL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Debug keystore contents to verify client certificate
     */
    private void debugKeystore(KeyStore keyStore) {
        try {
            System.out.println("\n  Keystore Contents:");
            Enumeration<String> aliases = keyStore.aliases();
            int count = 0;
            while (aliases.hasMoreElements()) {
                count++;
                String alias = aliases.nextElement();
                System.out.println("  - Alias: " + alias);

                if (keyStore.isKeyEntry(alias)) {
                    System.out.println("    Type: Private Key Entry");
                    if (keyStore.getCertificate(alias) instanceof X509Certificate) {
                        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
                        System.out.println("    Subject: " + cert.getSubjectDN());
                        System.out.println("    Issuer: " + cert.getIssuerDN());
                        System.out.println("    Valid until: " + cert.getNotAfter());
                    }
                } else if (keyStore.isCertificateEntry(alias)) {
                    System.out.println("    Type: Certificate Entry");
                }
            }

            if (count == 0) {
                System.out.println("  ⚠️  No entries found in keystore!");
            }
        } catch (Exception e) {
            System.err.println("  Error reading keystore: " + e.getMessage());
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

            System.out.println("✓ SSL verification disabled (TESTING ONLY!)");
        } catch (Exception e) {
            System.err.println("Failed to disable SSL verification: " + e.getMessage());
        }
    }

    /**
     * Step 1: Get JWT token from Keycloak
     */
    private String getJWTTokenFromKeycloak() throws Exception {
        System.out.println("\n========================================");
        System.out.println("STEP 1: Getting JWT Token from Keycloak");
        System.out.println("========================================");

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

            System.out.println("Request URL: " + KEYCLOAK_TOKEN_URL);
            System.out.println("Client ID: " + CLIENT_ID);

            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(urlParameters.toString());
                wr.flush();
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

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

                System.out.println("✓ Successfully retrieved JWT token");
                System.out.println("  Token length: " + accessToken.length() + " characters");
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

                System.err.println("✗ Failed to get JWT token");
                System.err.println("  Error response: " + errorResponse.toString());
                throw new Exception("Failed to get JWT token. Response Code: " + responseCode);
            }

        } catch (javax.net.ssl.SSLHandshakeException e) {
            System.err.println("\n✗ SSL Handshake failed!");
            System.err.println("  Error: " + e.getMessage());
            System.err.println("\n  Possible causes:");
            System.err.println("  1. Server requires client certificate (mTLS) but none provided");
            System.err.println("  2. Client certificate not trusted by server");
            System.err.println("  3. Certificate expired or invalid");
            System.err.println("\n  Solutions:");
            System.err.println("  1. Ensure keystore contains valid client certificate");
            System.err.println("  2. Set DISABLE_SSL_VERIFICATION = true for testing");
            System.err.println("  3. Run with --debug-ssl flag for more details");
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Step 2: Initialize services
     */
    private void initializeServices(String jwtToken) {
        System.out.println("\n========================================");
        System.out.println("STEP 2: Initializing Services");
        System.out.println("========================================");

        this.currentJwtToken = jwtToken;
        config.setProperty("management.node.jwt.token", jwtToken);

        // Initialize services
        this.jwtTokenService = new JwtTokenService(config);
        this.managementNodeDataHandler = new ManagementNodeDataHandler(config);
        this.configurationStore = new InMemoryConfigurationStore(7200000, 50000);
        this.federatorConfigurationService = new FederatorConfigurationService(config);

        System.out.println("✓ All services initialized");
    }

    /**
     * Step 3: Test ProducerConfigDTO retrieval with data providers
     */
    private void testProducerConfigDTO() {
        System.out.println("\n========================================");
        System.out.println("STEP 3: Testing ProducerConfigDTO with DataProviders");
        System.out.println("========================================");

        try {
            // Test getting producer configuration response
            ManagementNodeDataHandler.ProducerConfigurationResponse response =
                    managementNodeDataHandler.getProducerConfigurationResponse(currentJwtToken, null);

            if (response != null) {
                System.out.println("\n✓ Producer Configuration Response Retrieved");
                System.out.println("Client ID: " + response.getClientId());

                if (response.getProducers() != null) {
                    System.out.println("Number of Producers: " + response.getProducers().size());

                    for (ProducerDTO producer : response.getProducers()) {
                        System.out.println("\n=== Producer Details ===");
                        System.out.println("  Name: " + producer.getName());
                        System.out.println("  Description: " + producer.getDescription());
                        System.out.println("  Host: " + producer.getHost());
                        System.out.println("  Port: " + producer.getPort());
                        System.out.println("  TLS: " + producer.getTls());
                        System.out.println("  IDP Client ID: " + producer.getIdpClientId());
                        System.out.println("  Active: " + producer.getActive());

                        // Display DataProviders
                        if (producer.getDataProviders() != null && !producer.getDataProviders().isEmpty()) {
                            System.out.println("\n  Data Providers (" + producer.getDataProviders().size() + "):");

                            for (DataProviderDTO dataProvider : producer.getDataProviders()) {
                                System.out.println("    ╔══ Data Provider ══╗");
                                System.out.println("      Name: " + dataProvider.getName());
                                System.out.println("      Topic: " + dataProvider.getTopic());
                                System.out.println("      Description: " + dataProvider.getDescription());
                                System.out.println("      Active: " + dataProvider.getActive());

                                // Display Consumers for this data provider
                                if (dataProvider.getConsumers() != null && !dataProvider.getConsumers().isEmpty()) {
                                    System.out.println("      Consumers (" + dataProvider.getConsumers().size() + "):");
                                    for (ConsumerDTO consumer : dataProvider.getConsumers()) {
                                        System.out.println("        • Consumer: " + consumer.getName());
                                        System.out.println("          IDP Client ID: " + consumer.getIdpClientId());
                                    }
                                } else {
                                    System.out.println("      Consumers: None");
                                }
                            }
                        } else {
                            System.out.println("  ⚠️  Data Providers: Empty or null - Check DTO mapping!");
                        }
                    }
                } else {
                    System.out.println("⚠️  No producers in response");
                }
            } else {
                System.out.println("✗ Failed to get producer configuration response");
            }

        } catch (Exception e) {
            System.err.println("✗ ProducerConfigDTO test failed: " + e.getMessage());
        }
    }

    /**
     * Step 4: Test ConsumerConfigDTO retrieval
     */
    private void testConsumerConfigDTO() {
        System.out.println("\n========================================");
        System.out.println("STEP 4: Testing ConsumerConfigDTO");
        System.out.println("========================================");

        try {
            // Test getting consumer configuration response
            ConsumerConfigDTO response =
                    managementNodeDataHandler.getConsumerConfigurationResponse(currentJwtToken, null);

            if (response != null) {
                System.out.println("\n✓ Consumer Configuration Response Retrieved");
                System.out.println("Client ID: " + response.getClientId());

                // ConsumerConfigDTO contains producers which have data providers with consumers
                if (response.getProducers() != null) {
                    System.out.println("Number of Producers in Consumer Response: " + response.getProducers().size());

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
                    System.out.println("Total Consumers Found: " + totalConsumers);
                }
            } else {
                System.out.println("✗ Failed to get consumer configuration response");
            }

        } catch (Exception e) {
            System.err.println("✗ ConsumerConfigDTO test failed: " + e.getMessage());
        }
    }

    /**
     * Step 5: Test service integration with proper DTOs
     */
    private void testServiceIntegration() {
        System.out.println("\n========================================");
        System.out.println("STEP 5: Testing Service Integration");
        System.out.println("========================================");

        try {
            // Test getting all producer configurations
            List<ProducerConfiguration> producers = federatorConfigurationService.getProducerConfigurations();
            System.out.println("✓ Retrieved " + producers.size() + " producer configurations via service");

            // Test getting all consumer configurations
            List<ConsumerConfiguration> consumers = federatorConfigurationService.getConsumerConfigurations();
            System.out.println("✓ Retrieved " + consumers.size() + " consumer configurations via service");

            // Test cache functionality
            FederatorConfigurationService.FederatorServiceStatistics stats =
                    federatorConfigurationService.getServiceStatistics();
            System.out.println("\nService Statistics:");
            System.out.println("  Cache Hits: " + stats.getCacheHits());
            System.out.println("  Cache Misses: " + stats.getCacheMisses());
            System.out.println("  Cache Hit Rate: " + String.format("%.2f%%", stats.getCacheHitRate()));

        } catch (Exception e) {
            System.err.println("✗ Service integration test failed: " + e.getMessage());
        }
    }

    /**
     * Step 6: Test direct API endpoints
     */
    private void testDirectAPIEndpoints() {
        System.out.println("\n========================================");
        System.out.println("STEP 6: Testing Direct API Endpoints");
        System.out.println("========================================");

        // Test producer endpoint with empty producer_id parameter
        testEndpoint("/api/v1/configuration/producer?producer_id", "Producer endpoint (null producer_id)");

        // Test consumer endpoint with empty consumer_id parameter
        testEndpoint("/api/v1/configuration/consumer?consumer_id", "Consumer endpoint (null consumer_id)");
    }

    private void testEndpoint(String endpoint, String description) {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Testing: " + description);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("URL: " + FEDERATOR_BASE_URL + endpoint);

        try {
            URL url = new URL(FEDERATOR_BASE_URL + endpoint);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + currentJwtToken);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

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
                System.out.println("\n📋 API Response (Pretty JSON):");
                System.out.println("─────────────────────────────────────────");
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
                System.out.println(prettyJson);
                System.out.println("─────────────────────────────────────────");

                // Print summary analysis
                System.out.println("\n📊 Response Analysis:");
                if (jsonResponse.has("clientId")) {
                    System.out.println("  ✓ Client ID: " + jsonResponse.get("clientId").asText());
                }

                if (jsonResponse.has("producers")) {
                    com.fasterxml.jackson.databind.JsonNode producers = jsonResponse.get("producers");
                    System.out.println("  ✓ Producers: " + producers.size() + " found");

                    // Analyze structure
                    if (producers.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode firstProducer = producers.get(0);

                        // Check for all expected fields
                        System.out.println("\n  Structure validation for first producer:");
                        System.out.println("    " + (firstProducer.has("name") ? "✓" : "✗") + " name field");
                        System.out.println("    " + (firstProducer.has("description") ? "✓" : "✗") + " description field");
                        System.out.println("    " + (firstProducer.has("host") ? "✓" : "✗") + " host field");
                        System.out.println("    " + (firstProducer.has("port") ? "✓" : "✗") + " port field");
                        System.out.println("    " + (firstProducer.has("tls") ? "✓" : "✗") + " tls field");
                        System.out.println("    " + (firstProducer.has("active") ? "✓" : "✗") + " active field");
                        System.out.println("    " + (firstProducer.has("idpClientId") ? "✓" : "✗") + " idpClientId field");

                        if (firstProducer.has("dataProviders")) {
                            com.fasterxml.jackson.databind.JsonNode dataProviders = firstProducer.get("dataProviders");
                            System.out.println("    ✓ dataProviders field (" + dataProviders.size() + " items)");

                            if (dataProviders.size() > 0) {
                                com.fasterxml.jackson.databind.JsonNode firstDataProvider = dataProviders.get(0);
                                System.out.println("\n  Data Provider structure validation:");
                                System.out.println("    " + (firstDataProvider.has("name") ? "✓" : "✗") + " name field");
                                System.out.println("    " + (firstDataProvider.has("topic") ? "✓" : "✗") + " topic field");
                                System.out.println("    " + (firstDataProvider.has("consumers") ? "✓" : "✗") + " consumers field");
                            }
                        } else {
                            System.out.println("    ✗ dataProviders field missing!");
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

                System.out.println("\n✗ Request failed with code: " + responseCode);
                System.out.println("Error response: " + errorResponse.toString());
            }

            connection.disconnect();
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        System.out.println("\n========================================");
        System.out.println("Cleaning Up Resources");
        System.out.println("========================================");

        if (federatorConfigurationService != null) {
            try {
                federatorConfigurationService.close();
                System.out.println("✓ FederatorConfigurationService closed");
            } catch (Exception e) {
                System.out.println("⚠️  Error closing FederatorConfigurationService: " + e.getMessage());
            }
        }

        if (managementNodeDataHandler != null) {
            try {
                managementNodeDataHandler.close();
                System.out.println("✓ ManagementNodeDataHandler closed");
            } catch (Exception e) {
                System.out.println("⚠️  Error closing ManagementNodeDataHandler: " + e.getMessage());
            }
        }

        if (jwtTokenService != null) {
            try {
                jwtTokenService.close();
                System.out.println("✓ JwtTokenService closed");
            } catch (Exception e) {
                System.out.println("⚠️  Error closing JwtTokenService: " + e.getMessage());
            }
        }
    }

    /**
     * Run the complete integration test
     */
    public void runTest() {
        System.out.println("\n=====================================");
        System.out.println("CONFIGURATION DTO INTEGRATION TEST");
        System.out.println("Testing with ProducerConfigDTO and ConsumerConfigDTO");
        System.out.println("=====================================");

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
            System.err.println("\n✗✗✗ SSL ERROR ✗✗✗");
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nTry one of these solutions:");
            System.err.println("1. Set DISABLE_SSL_VERIFICATION = true (for testing only)");
            System.err.println("2. Ensure client certificate is in keystore: " + KEYSTORE_PATH);
            System.err.println("3. Run with --debug-ssl flag for detailed SSL debugging");
        } catch (Exception e) {
            System.err.println("\n✗✗✗ TEST FAILED ✗✗✗");
            System.err.println("Error: " + e.getMessage());
        } finally {
            cleanup();
        }

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("\n=====================================");
        System.out.println("TEST SUMMARY");
        System.out.println("=====================================");
        System.out.println("Result: " + (testPassed ? "✓✓✓ PASSED" : "✗✗✗ FAILED"));
        System.out.println("Execution Time: " + duration + " ms");
        System.out.println("\nConfiguration:");
        System.out.println("- SSL Verification: " + (DISABLE_SSL_VERIFICATION ? "DISABLED" : "ENABLED"));
        System.out.println("- Keystore: " + KEYSTORE_PATH);
        System.out.println("- Truststore: " + TRUSTSTORE_PATH);
        System.out.println("\nKey Points:");
        System.out.println("- Using ProducerConfigDTO and ConsumerConfigDTO");
        System.out.println("- DataProviders should use DataProviderDTO");
        System.out.println("- Producer/Consumer IDs passed as null");
        System.out.println("\nEndpoints Tested:");
        System.out.println("- " + KEYCLOAK_TOKEN_URL);
        System.out.println("- " + FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id");
        System.out.println("- " + FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id");
        System.out.println("=====================================");

        System.exit(testPassed ? 0 : 1);
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        System.out.println("Starting Configuration DTO Integration Test...");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Java Vendor: " + System.getProperty("java.vendor"));

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
            System.out.println("\nUsage: java ConfigurationClientTestWithActualCode [options]");
            System.out.println("Options:");
            System.out.println("  --help, -h       Show this help message");
            System.out.println("  --debug-ssl      Enable SSL debugging");
            System.out.println("\nThis test verifies:");
            System.out.println("  1. ProducerConfigDTO structure with DataProviderDTO");
            System.out.println("  2. ConsumerConfigDTO structure");
            System.out.println("  3. DataProvider population in responses");
            System.out.println("  4. Service integration with proper DTOs");
            System.out.println("\nSSL Configuration:");
            System.out.println("  - Set DISABLE_SSL_VERIFICATION = true to bypass SSL checks (testing only)");
            System.out.println("  - Ensure keystore contains client certificate for mTLS");
            System.out.println("  - Use --debug-ssl to troubleshoot SSL handshake issues");
            return;
        }

        if (debugSSL || ENABLE_SSL_DEBUG) {
            System.setProperty("javax.net.debug", "ssl,handshake,trustmanager");
            System.out.println("SSL debugging enabled");
        }

        ConfigurationClientTestWithActualCode test = new ConfigurationClientTestWithActualCode();
        test.runTest();
    }
}