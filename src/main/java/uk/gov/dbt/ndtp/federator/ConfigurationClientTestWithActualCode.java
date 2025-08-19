package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.exceptions.ManagementNodeException;
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
    public static final String S = "=====================================";

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
        initializeSSL();
        setupConfiguration();
    }

    private void initializeSSL() {
        if (DISABLE_SSL_VERIFICATION) {
            LOGGER.info("⚠️  WARNING: SSL verification is disabled!");
            disableSSLVerification();
        } else {
            boolean sslSetup = setupSSLWithValidation();
            if (!sslSetup) {
                LOGGER.error("⚠️  SSL setup failed, attempting to continue...");
            }
        }
    }

    private void setupConfiguration() {
        setupManagementNodeConfig();
        setupJwtTokenServiceConfig();
        setupFederatorConfigServiceConfig();
    }

    private void setupManagementNodeConfig() {
        config.setProperty("management.node.base.url", FEDERATOR_BASE_URL);
        config.setProperty("management.node.request.timeout", "30");
        config.setProperty("management.node.max.retry.attempts", "3");
        config.setProperty("management.node.retry.delay.seconds", "2");
        config.setProperty("management.node.thread.pool.size", "5");
    }

    private void setupJwtTokenServiceConfig() {
        config.setProperty("management.node.jwt.token", "");
        config.setProperty("management.node.jwt.token.refresh.buffer", "300");
        config.setProperty("management.node.jwt.token.auto.refresh.enabled", "true");
        config.setProperty("management.node.jwt.token.file.watching.enabled", "false");
    }

    private void setupFederatorConfigServiceConfig() {
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
            KeyStore trustStore = loadTrustStore();
            TrustManagerFactory tmf = createTrustManagerFactory(trustStore);
            KeyManager[] keyManagers = loadKeyManagers();
            configureSslContext(keyManagers, tmf);
            setSystemSslProperties();
            LOGGER.info("✓ SSL setup completed successfully!");
            LOGGER.info("========================\n");
            return true;
        } catch (Exception e) {
            LOGGER.error("✗ Failed to setup SSL: " + e.getMessage());
            return false;
        }
    }

    private KeyStore loadTrustStore() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        if (!Files.exists(Paths.get(TRUSTSTORE_PATH))) {
            throw new Exception("Truststore not found at: " + TRUSTSTORE_PATH);
        }
        try (FileInputStream trustStoreStream = new FileInputStream(TRUSTSTORE_PATH)) {
            trustStore.load(trustStoreStream, TRUSTSTORE_PW.toCharArray());
            LOGGER.info("✓ Truststore loaded successfully");
        }
        return trustStore;
    }

    private TrustManagerFactory createTrustManagerFactory(KeyStore trustStore) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    private KeyManager[] loadKeyManagers() throws Exception {
        if (!Files.exists(Paths.get(KEYSTORE_PATH))) {
            LOGGER.info("⚠️  Keystore not found at: " + KEYSTORE_PATH);
            LOGGER.info("   Proceeding without client certificate (server may reject if mTLS required)");
            return null;
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream keyStoreStream = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(keyStoreStream, KEYSTORE_PW.toCharArray());
            debugKeystore(keyStore);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, KEYSTORE_PW.toCharArray());
            LOGGER.info("✓ Client certificate keystore loaded successfully");
            return kmf.getKeyManagers();
        }
    }

    private void configureSslContext(KeyManager[] keyManagers, TrustManagerFactory tmf) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, tmf.getTrustManagers(), new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
    }

    private void setSystemSslProperties() {
        System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH);
        System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PW);
        if (Files.exists(Paths.get(KEYSTORE_PATH))) {
            System.setProperty("javax.net.ssl.keyStore", KEYSTORE_PATH);
            System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PW);
        }
    }

    /**
     * Debug keystore contents to verify client certificate
     */
    private void debugKeystore(KeyStore keyStore) {
        try {
            LOGGER.info("\n  Keystore Contents:");
            Enumeration<String> aliases = keyStore.aliases();
            int count = processKeystoreAliases(keyStore, aliases);
            if (count == 0) {
                LOGGER.info("  ⚠️  No entries found in keystore!");
            }
        } catch (Exception e) {
            LOGGER.error("  Error reading keystore: " + e.getMessage());
        }
    }

    private int processKeystoreAliases(KeyStore keyStore, Enumeration<String> aliases) throws Exception {
        int count = 0;
        while (aliases.hasMoreElements()) {
            count++;
            String alias = aliases.nextElement();
            LOGGER.info("  - Alias: " + alias);
            logKeystoreEntry(keyStore, alias);
        }
        return count;
    }

    private void logKeystoreEntry(KeyStore keyStore, String alias) throws Exception {
        if (keyStore.isKeyEntry(alias)) {
            LOGGER.info("    Type: Private Key Entry");
            logCertificateDetails(keyStore.getCertificate(alias));
        } else if (keyStore.isCertificateEntry(alias)) {
            LOGGER.info("    Type: Certificate Entry");
        }
    }

    private void logCertificateDetails(java.security.cert.Certificate cert) {
        if (cert instanceof X509Certificate) {
            X509Certificate x509Cert = (X509Certificate) cert;
            LOGGER.info("    Subject: " + x509Cert.getSubjectDN());
            LOGGER.info("    Issuer: " + x509Cert.getIssuerDN());
            LOGGER.info("    Valid until: " + x509Cert.getNotAfter());
        }
    }

    /**
     * Disable SSL verification for testing purposes only
     */
    private void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = createTrustAllManagers();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            LOGGER.info("✓ SSL verification disabled (TESTING ONLY!)");
        } catch (Exception e) {
            LOGGER.error("Failed to disable SSL verification: " + e.getMessage());
        }
    }

    private TrustManager[] createTrustAllManagers() {
        return new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
    }

    /**
     * Step 1: Get JWT token from Keycloak
     */
    private String getJWTTokenFromKeycloak() throws Exception {
        logStep("STEP 1: Getting JWT Token from Keycloak");
        HttpsURLConnection connection = createKeycloakConnection();
        try {
            sendKeycloakRequest(connection);
            return processKeycloakResponse(connection);
        } catch (javax.net.ssl.SSLHandshakeException e) {
            handleSslHandshakeException(e);
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    private HttpsURLConnection createKeycloakConnection() throws Exception {
        URL url = new URL(KEYCLOAK_TOKEN_URL);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        return connection;
    }

    private void sendKeycloakRequest(HttpsURLConnection connection) throws Exception {
        String urlParameters = buildKeycloakParameters();
        LOGGER.info("Request URL: " + KEYCLOAK_TOKEN_URL);
        LOGGER.info("Client ID: " + CLIENT_ID);
        try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
            wr.writeBytes(urlParameters);
            wr.flush();
        }
    }

    private String buildKeycloakParameters() throws Exception {
        StringBuilder urlParameters = new StringBuilder();
        urlParameters.append("client_id=").append(URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8.toString()));
        urlParameters.append("&grant_type=client_credentials");
        if (CLIENT_SECRET != null && !CLIENT_SECRET.trim().isEmpty()) {
            urlParameters.append("&client_secret=").append(URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8.toString()));
        }
        return urlParameters.toString();
    }

    private String processKeycloakResponse(HttpsURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        LOGGER.info("Response Code: " + responseCode);

        if (responseCode == 200) {
            return extractTokenFromResponse(connection);
        } else {
            handleKeycloakError(connection, responseCode);
            throw new Exception("Failed to get JWT token. Response Code: " + responseCode);
        }
    }

    private String extractTokenFromResponse(HttpsURLConnection connection) throws Exception {
        String response = readResponse(connection.getInputStream());
        com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(response);
        String accessToken = jsonResponse.get("access_token").asText();
        LOGGER.info("✓ Successfully retrieved JWT token");
        LOGGER.info("  Token length: " + accessToken.length() + " characters");
        return accessToken;
    }

    private void handleKeycloakError(HttpsURLConnection connection, int responseCode) throws Exception {
        String errorResponse = readResponse(connection.getErrorStream());
        LOGGER.error("✗ Failed to get JWT token");
        LOGGER.error("  Error response: " + errorResponse);
    }

    private void handleSslHandshakeException(javax.net.ssl.SSLHandshakeException e) {
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
    }

    /**
     * Step 2: Initialize services
     */
    private void initializeServices(String jwtToken) {
        logStep("STEP 2: Initializing Services");
        this.currentJwtToken = jwtToken;
        config.setProperty("management.node.jwt.token", jwtToken);
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
        logStep("STEP 3: Testing ProducerConfigDTO with DataProviders");
        try {
            ManagementNodeDataHandler.ProducerConfigurationResponse response =
                    managementNodeDataHandler.getProducerConfigurationResponse(currentJwtToken, null);
            processProducerResponse(response);
        } catch (Exception e) {
            LOGGER.error("✗ ProducerConfigDTO test failed: " + e.getMessage());
        }
    }

    private void processProducerResponse(ManagementNodeDataHandler.ProducerConfigurationResponse response) {
        if (response == null) {
            LOGGER.info("✗ Failed to get producer configuration response");
            return;
        }

        LOGGER.info("\n✓ Producer Configuration Response Retrieved");
        LOGGER.info("Client ID: " + response.getClientId());

        if (response.getProducers() != null) {
            LOGGER.info("Number of Producers: " + response.getProducers().size());
            response.getProducers().forEach(this::logProducerDetails);
        } else {
            LOGGER.info("⚠️  No producers in response");
        }
    }

    private void logProducerDetails(ProducerDTO producer) {
        LOGGER.info("\n=== Producer Details ===");
        logProducerBasicInfo(producer);
        logDataProviders(producer);
    }

    private void logProducerBasicInfo(ProducerDTO producer) {
        LOGGER.info("  Name: " + producer.getName());
        LOGGER.info("  Description: " + producer.getDescription());
        LOGGER.info("  Host: " + producer.getHost());
        LOGGER.info("  Port: " + producer.getPort());
        LOGGER.info("  TLS: " + producer.getTls());
        LOGGER.info("  IDP Client ID: " + producer.getIdpClientId());
        LOGGER.info("  Active: " + producer.getActive());
    }

    private void logDataProviders(ProducerDTO producer) {
        if (producer.getDataProviders() == null || producer.getDataProviders().isEmpty()) {
            LOGGER.info("  ⚠️  Data Providers: Empty or null - Check DTO mapping!");
            return;
        }

        LOGGER.info("\n  Data Providers (" + producer.getDataProviders().size() + "):");
        producer.getDataProviders().forEach(this::logDataProviderDetails);
    }

    private void logDataProviderDetails(DataProviderDTO dataProvider) {
        LOGGER.info("    ╔══ Data Provider ══╗");
        LOGGER.info("      Name: " + dataProvider.getName());
        LOGGER.info("      Topic: " + dataProvider.getTopic());
        LOGGER.info("      Description: " + dataProvider.getDescription());
        LOGGER.info("      Active: " + dataProvider.getActive());
        logDataProviderConsumers(dataProvider);
    }

    private void logDataProviderConsumers(DataProviderDTO dataProvider) {
        if (dataProvider.getConsumers() == null || dataProvider.getConsumers().isEmpty()) {
            LOGGER.info("      Consumers: None");
            return;
        }

        LOGGER.info("      Consumers (" + dataProvider.getConsumers().size() + "):");
        for (ConsumerDTO consumer : dataProvider.getConsumers()) {
            LOGGER.info("        • Consumer: " + consumer.getName());
            LOGGER.info("          IDP Client ID: " + consumer.getIdpClientId());
        }
    }

    /**
     * Step 4: Test ConsumerConfigDTO retrieval
     */
    private void testConsumerConfigDTO() {
        logStep("STEP 4: Testing ConsumerConfigDTO");
        try {
            ConsumerConfigDTO response =
                    managementNodeDataHandler.getConsumerConfigurationResponse(currentJwtToken, null);
            processConsumerResponse(response);
        } catch (Exception e) {
            LOGGER.error("✗ ConsumerConfigDTO test failed: " + e.getMessage());
        }
    }

    private void processConsumerResponse(ConsumerConfigDTO response) {
        if (response == null) {
            LOGGER.info("✗ Failed to get consumer configuration response");
            return;
        }

        LOGGER.info("\n✓ Consumer Configuration Response Retrieved");
        LOGGER.info("Client ID: " + response.getClientId());

        if (response.getProducers() != null) {
            int totalConsumers = countTotalConsumers(response.getProducers());
            LOGGER.info("Number of Producers in Consumer Response: " + response.getProducers().size());
            LOGGER.info("Total Consumers Found: " + totalConsumers);
        }
    }

    private int countTotalConsumers(List<ProducerDTO> producers) {
        int totalConsumers = 0;
        for (ProducerDTO producer : producers) {
            if (producer.getDataProviders() != null) {
                for (DataProviderDTO dataProvider : producer.getDataProviders()) {
                    if (dataProvider.getConsumers() != null) {
                        totalConsumers += dataProvider.getConsumers().size();
                    }
                }
            }
        }
        return totalConsumers;
    }

    /**
     * Step 5: Test service integration with proper DTOs
     */
    private void testServiceIntegration() {
        logStep("STEP 5: Testing Service Integration");
        try {
            testProducerConfigurations();
            testConsumerConfigurations();
            testCacheFunctionality();
        } catch (Exception e) {
            LOGGER.error("✗ Service integration test failed: " + e.getMessage());
        }
    }

    private void testProducerConfigurations() throws ManagementNodeException {
        List<ProducerConfiguration> producers = federatorConfigurationService.getProducerConfigurations();
        LOGGER.info("✓ Retrieved " + producers.size() + " producer configurations via service");
    }

    private void testConsumerConfigurations() throws ManagementNodeException {
        List<ConsumerConfiguration> consumers = federatorConfigurationService.getConsumerConfigurations();
        LOGGER.info("✓ Retrieved " + consumers.size() + " consumer configurations via service");
    }

    private void testCacheFunctionality() {
        FederatorConfigurationService.FederatorServiceStatistics stats =
                federatorConfigurationService.getServiceStatistics();
        LOGGER.info("\nService Statistics:");
        LOGGER.info("  Cache Hits: " + stats.getCacheHits());
        LOGGER.info("  Cache Misses: " + stats.getCacheMisses());
        LOGGER.info("  Cache Hit Rate: " + String.format("%.2f%%", stats.getCacheHitRate()));
    }

    /**
     * Step 6: Test direct API endpoints
     */
    private void testDirectAPIEndpoints() {
        logStep("STEP 6: Testing Direct API Endpoints");
        testEndpoint("/api/v1/configuration/producer?producer_id", "Producer endpoint (null producer_id)");
        testEndpoint("/api/v1/configuration/consumer?consumer_id", "Consumer endpoint (null consumer_id)");
    }

    private void testEndpoint(String endpoint, String description) {
        logEndpointTest(endpoint, description);
        try {
            HttpsURLConnection connection = createEndpointConnection(endpoint);
            processEndpointResponse(connection);
            connection.disconnect();
        } catch (Exception e) {
            LOGGER.info("✗ Error: " + e.getMessage());
        }
    }

    private void logEndpointTest(String endpoint, String description) {
        LOGGER.info("\n┌────────────────────────────────────────");
        LOGGER.info("Testing: " + description);
        LOGGER.info("└────────────────────────────────────────");
        LOGGER.info("URL: " + FEDERATOR_BASE_URL + endpoint);
    }

    private HttpsURLConnection createEndpointConnection(String endpoint) throws Exception {
        URL url = new URL(FEDERATOR_BASE_URL + endpoint);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + currentJwtToken);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        return connection;
    }

    private void processEndpointResponse(HttpsURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        LOGGER.info("Response Code: " + responseCode);

        if (responseCode == 200) {
            handleSuccessfulEndpointResponse(connection);
        } else {
            handleEndpointError(connection, responseCode);
        }
    }

    private void handleSuccessfulEndpointResponse(HttpsURLConnection connection) throws Exception {
        String response = readResponse(connection.getInputStream());
        com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(response);
        logJsonResponse(jsonResponse);
        analyzeJsonResponse(jsonResponse);
    }

    private void logJsonResponse(com.fasterxml.jackson.databind.JsonNode jsonResponse) throws Exception {
        LOGGER.info("\n📋 API Response (Pretty JSON):");
        LOGGER.info("─────────────────────────────────────────");
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
        LOGGER.info(prettyJson);
        LOGGER.info("─────────────────────────────────────────");
    }

    private void analyzeJsonResponse(com.fasterxml.jackson.databind.JsonNode jsonResponse) {
        LOGGER.info("\n📊 Response Analysis:");
        if (jsonResponse.has("clientId")) {
            LOGGER.info("  ✓ Client ID: " + jsonResponse.get("clientId").asText());
        }
        if (jsonResponse.has("producers")) {
            analyzeProducers(jsonResponse.get("producers"));
        }
    }

    private void analyzeProducers(com.fasterxml.jackson.databind.JsonNode producers) {
        LOGGER.info("  ✓ Producers: " + producers.size() + " found");
        if (producers.size() > 0) {
            validateProducerStructure(producers.get(0));
        }
    }

    private void validateProducerStructure(com.fasterxml.jackson.databind.JsonNode producer) {
        LOGGER.info("\n  Structure validation for first producer:");
        validateField(producer, "name");
        validateField(producer, "description");
        validateField(producer, "host");
        validateField(producer, "port");
        validateField(producer, "tls");
        validateField(producer, "active");
        validateField(producer, "idpClientId");
        validateDataProviders(producer);
    }

    private void validateField(com.fasterxml.jackson.databind.JsonNode node, String fieldName) {
        LOGGER.info("    " + (node.has(fieldName) ? "✓" : "✗") + " " + fieldName + " field");
    }

    private void validateDataProviders(com.fasterxml.jackson.databind.JsonNode producer) {
        if (!producer.has("dataProviders")) {
            LOGGER.info("    ✗ dataProviders field missing!");
            return;
        }

        com.fasterxml.jackson.databind.JsonNode dataProviders = producer.get("dataProviders");
        LOGGER.info("    ✓ dataProviders field (" + dataProviders.size() + " items)");

        if (dataProviders.size() > 0) {
            validateDataProviderStructure(dataProviders.get(0));
        }
    }

    private void validateDataProviderStructure(com.fasterxml.jackson.databind.JsonNode dataProvider) {
        LOGGER.info("\n  Data Provider structure validation:");
        validateField(dataProvider, "name");
        validateField(dataProvider, "topic");
        validateField(dataProvider, "consumers");
    }

    private void handleEndpointError(HttpsURLConnection connection, int responseCode) throws Exception {
        String errorResponse = readResponse(connection.getErrorStream());
        LOGGER.info("\n✗ Request failed with code: " + responseCode);
        LOGGER.info("Error response: " + errorResponse);
    }

    private String readResponse(java.io.InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        logStep("Cleaning Up Resources");
        closeFederatorConfigurationService();
        closeManagementNodeDataHandler();
        closeJwtTokenService();
    }

    private void closeFederatorConfigurationService() {
        if (federatorConfigurationService != null) {
            try {
                federatorConfigurationService.close();
                LOGGER.info("✓ FederatorConfigurationService closed");
            } catch (Exception e) {
                LOGGER.info("⚠️  Error closing FederatorConfigurationService: " + e.getMessage());
            }
        }
    }

    private void closeManagementNodeDataHandler() {
        if (managementNodeDataHandler != null) {
            try {
                managementNodeDataHandler.close();
                LOGGER.info("✓ ManagementNodeDataHandler closed");
            } catch (Exception e) {
                LOGGER.info("⚠️  Error closing ManagementNodeDataHandler: " + e.getMessage());
            }
        }
    }

    private void closeJwtTokenService() {
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
        logTestHeader();
        long startTime = System.currentTimeMillis();
        boolean testPassed = executeTest();
        long duration = System.currentTimeMillis() - startTime;
        logTestSummary(testPassed, duration);
        System.exit(testPassed ? 0 : 1);
    }

    private boolean executeTest() {
        try {
            String jwtToken = getJWTTokenFromKeycloak();
            initializeServices(jwtToken);
            testProducerConfigDTO();
            testConsumerConfigDTO();
            testServiceIntegration();
            testDirectAPIEndpoints();
            return true;
        } catch (javax.net.ssl.SSLException e) {
            handleSslException(e);
            return false;
        } catch (Exception e) {
            handleGeneralException(e);
            return false;
        } finally {
            cleanup();
        }
    }

    private void handleSslException(javax.net.ssl.SSLException e) {
        LOGGER.error("\n✗✗✗ SSL ERROR ✗✗✗");
        LOGGER.error("Error: " + e.getMessage());
        LOGGER.error("\nTry one of these solutions:");
        LOGGER.error("1. Set DISABLE_SSL_VERIFICATION = true (for testing only)");
        LOGGER.error("2. Ensure client certificate is in keystore: " + KEYSTORE_PATH);
        LOGGER.error("3. Run with --debug-ssl flag for detailed SSL debugging");
    }

    private void handleGeneralException(Exception e) {
        LOGGER.error("\n✗✗✗ TEST FAILED ✗✗✗");
        LOGGER.error("Error: " + e.getMessage());
    }

    private void logTestHeader() {
        LOGGER.info("\n=====================================");
        LOGGER.info("CONFIGURATION DTO INTEGRATION TEST");
        LOGGER.info("Testing with ProducerConfigDTO and ConsumerConfigDTO");
        LOGGER.info(S);
    }

    private void logTestSummary(boolean testPassed, long duration) {
        LOGGER.info("\n=====================================");
        LOGGER.info("TEST SUMMARY");
        LOGGER.info(S);
        logTestResult(testPassed, duration);
        logTestConfiguration();
        logKeyPoints();
        logTestedEndpoints();
        LOGGER.info(S);
    }

    private void logTestResult(boolean testPassed, long duration) {
        LOGGER.info("Result: " + (testPassed ? "✓✓✓ PASSED" : "✗✗✗ FAILED"));
        LOGGER.info("Execution Time: " + duration + " ms");
    }

    private void logTestConfiguration() {
        LOGGER.info("\nConfiguration:");
        LOGGER.info("- SSL Verification: " + (DISABLE_SSL_VERIFICATION ? "DISABLED" : "ENABLED"));
        LOGGER.info("- Keystore: " + KEYSTORE_PATH);
        LOGGER.info("- Truststore: " + TRUSTSTORE_PATH);
    }

    private void logKeyPoints() {
        LOGGER.info("\nKey Points:");
        LOGGER.info("- Using ProducerConfigDTO and ConsumerConfigDTO");
        LOGGER.info("- DataProviders should use DataProviderDTO");
        LOGGER.info("- Producer/Consumer IDs passed as null");
    }

    private void logTestedEndpoints() {
        LOGGER.info("\nEndpoints Tested:");
        LOGGER.info("- " + KEYCLOAK_TOKEN_URL);
        LOGGER.info("- " + FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id");
        LOGGER.info("- " + FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id");
    }

    private void logStep(String stepDescription) {
        LOGGER.info("\n========================================");
        LOGGER.info(stepDescription);
        LOGGER.info("========================================");
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        logStartupInfo();
        CommandLineArgs cmdArgs = parseCommandLineArgs(args);

        if (cmdArgs.showHelp) {
            showHelp();
            return;
        }

        configureSslDebug(cmdArgs.debugSSL);
        ConfigurationClientTestWithActualCode test = new ConfigurationClientTestWithActualCode();
        test.runTest();
    }

    private static void logStartupInfo() {
        LOGGER.info("Starting Configuration DTO Integration Test...");
        LOGGER.info("Java Version: " + System.getProperty("java.version"));
        LOGGER.info("Java Vendor: " + System.getProperty("java.vendor"));
    }

    private static CommandLineArgs parseCommandLineArgs(String[] args) {
        CommandLineArgs cmdArgs = new CommandLineArgs();
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                cmdArgs.showHelp = true;
            } else if (arg.equals("--debug-ssl")) {
                cmdArgs.debugSSL = true;
            }
        }
        return cmdArgs;
    }

    private static void configureSslDebug(boolean debugSSL) {
        if (debugSSL || ENABLE_SSL_DEBUG) {
            System.setProperty("javax.net.debug", "ssl,handshake,trustmanager");
            LOGGER.info("SSL debugging enabled");
        }
    }

    private static void showHelp() {
        LOGGER.info("\nUsage: java ConfigurationClientTestWithActualCode [options]");
        LOGGER.info("Options:");
        LOGGER.info("  --help, -h       Show this help message");
        LOGGER.info("  --debug-ssl      Enable SSL debugging");
        showTestInfo();
        showSslConfigInfo();
    }

    private static void showTestInfo() {
        LOGGER.info("\nThis test verifies:");
        LOGGER.info("  1. ProducerConfigDTO structure with DataProviderDTO");
        LOGGER.info("  2. ConsumerConfigDTO structure");
        LOGGER.info("  3. DataProvider population in responses");
        LOGGER.info("  4. Service integration with proper DTOs");
    }

    private static void showSslConfigInfo() {
        LOGGER.info("\nSSL Configuration:");
        LOGGER.info("  - Set DISABLE_SSL_VERIFICATION = true to bypass SSL checks (testing only)");
        LOGGER.info("  - Ensure keystore contains client certificate for mTLS");
        LOGGER.info("  - Use --debug-ssl to troubleshoot SSL handshake issues");
    }

    private static class CommandLineArgs {
        boolean showHelp = false;
        boolean debugSSL = false;
    }
}