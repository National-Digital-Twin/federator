package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;

/**
 * Test client to verify connectivity between Management Node and Federator.
 * This test retrieves all producers and consumers to display available data providers.
 */
public class ConfigurationClientTest2 {

    public static final Logger LOGGER = LoggerFactory.getLogger("ConfigurationClientTest2");

    // Configuration constants
    private static final String KEYCLOAK_TOKEN_URL = "https://localhost:8443/realms/management-node/protocol/openid-connect/token";
    private static final String FEDERATOR_BASE_URL = "https://localhost:8090";
    private static final String CLIENT_ID = "FEDERATOR_BCC";
    private static final String CLIENT_SECRET = ""; // Empty string or actual secret if required

    // SSL Configuration - Update these paths to your actual keystore locations
    private static final String TRUSTSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/truststore.jks";
    private static final String TRUSTSTORE_PW = "changeit";
    private static final String KEYSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/keystore.jks";
    private static final String KEYSTORE_PW = "changeit";

    // Option to disable SSL verification (for testing only)
    private static final boolean DISABLE_SSL_VERIFICATION = false;

    private final ObjectMapper objectMapper;
    private String currentJwtToken;

    public ConfigurationClientTest2() {
        this.objectMapper = new ObjectMapper();
        initializeSSL();
    }

    private void initializeSSL() {
        if (DISABLE_SSL_VERIFICATION) {
            LOGGER.info("⚠️  WARNING: SSL verification is disabled. This should only be used for testing!");
            disableSSLVerification();
        } else {
            if (!setupSSLWithValidation()) {
                LOGGER.info("⚠️  SSL setup failed, you may want to enable DISABLE_SSL_VERIFICATION for testing");
            }
        }
    }

    /**
     * Verify SSL files exist and are readable before setting them
     */
    private boolean verifySSLFiles() {
        logSection("Verifying SSL Files");
        boolean truststoreValid = verifyTruststore();
        boolean keystoreValid = verifyKeystore();
        return truststoreValid && keystoreValid;
    }

    private boolean verifyTruststore() {
        LOGGER.info("\nChecking Truststore:");
        LOGGER.info("  Path: " + TRUSTSTORE_PATH);

        Path truststorePath = Paths.get(TRUSTSTORE_PATH);
        if (!validateFileExists(truststorePath, "Truststore")) {
            return false;
        }

        return loadAndVerifyStore(TRUSTSTORE_PATH, TRUSTSTORE_PW, "Truststore");
    }

    private boolean verifyKeystore() {
        LOGGER.info("\nChecking Keystore (optional for client certs):");
        LOGGER.info("  Path: " + KEYSTORE_PATH);

        Path keystorePath = Paths.get(KEYSTORE_PATH);
        if (!Files.exists(keystorePath)) {
            LOGGER.info("  ⚠️  Keystore file does not exist (OK if no client cert required)");
            return true;
        }

        if (!Files.isReadable(keystorePath)) {
            LOGGER.error("  ✗ Keystore file exists but is NOT readable!");
            return false;
        }

        return loadAndVerifyStore(KEYSTORE_PATH, KEYSTORE_PW, "Keystore");
    }

    private boolean validateFileExists(Path filePath, String fileType) {
        if (!Files.exists(filePath)) {
            LOGGER.error("  ✗ " + fileType + " file does NOT exist!");
            if (fileType.equals("Truststore")) {
                LOGGER.error("    Create it with: keytool -import -file server.crt -alias server -keystore " +
                        TRUSTSTORE_PATH + " -storepass " + TRUSTSTORE_PW);
            }
            return false;
        }

        if (!Files.isReadable(filePath)) {
            LOGGER.error("  ✗ " + fileType + " file is NOT readable!");
            return false;
        }

        LOGGER.info("  ✓ " + fileType + " file exists and is readable");
        LOGGER.info("  File size: " + filePath.toFile().length() + " bytes");
        return true;
    }

    private boolean loadAndVerifyStore(String storePath, String storePassword, String storeType) {
        try {
            KeyStore store = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(storePath)) {
                store.load(fis, storePassword.toCharArray());
                LOGGER.info("  ✓ " + storeType + " password is correct");
                LOGGER.info("  " + storeType + " type: " + store.getType());
                LOGGER.info("  Number of entries: " + store.size());
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("  ✗ Failed to load " + storeType + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Setup SSL with proper validation and error handling
     */
    private boolean setupSSLWithValidation() {
        try {
            logSection("Setting up SSL with certificate verification");

            if (!verifySSLFiles()) {
                logSSLVerificationError();
                return false;
            }

            setSystemSSLProperties();
            SSLContext sslContext = createSSLContext();
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            LOGGER.info("\n✓ SSL setup completed successfully!");
            return true;
        } catch (Exception e) {
            LOGGER.error("\n✗ Failed to setup SSL: " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void logSSLVerificationError() {
        LOGGER.error("\n✗ SSL file verification failed!");
        LOGGER.error("Consider setting DISABLE_SSL_VERIFICATION = true for testing");
    }

    private void setSystemSSLProperties() {
        System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH);
        System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PW);

        if (Files.exists(Paths.get(KEYSTORE_PATH))) {
            System.setProperty("javax.net.ssl.keyStore", KEYSTORE_PATH);
            System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PW);
        }
    }

    private SSLContext createSSLContext() throws Exception {
        LOGGER.info("\nLoading Truststore...");
        KeyStore trustStore = loadKeyStore(TRUSTSTORE_PATH, TRUSTSTORE_PW);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        KeyManager[] keyManagers = loadKeyManagers();

        LOGGER.info("Creating SSL Context...");
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    private KeyStore loadKeyStore(String path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream stream = new FileInputStream(path)) {
            keyStore.load(stream, password.toCharArray());
        }
        return keyStore;
    }

    private KeyManager[] loadKeyManagers() throws Exception {
        if (!Files.exists(Paths.get(KEYSTORE_PATH))) {
            return null;
        }

        LOGGER.info("Loading Keystore...");
        KeyStore keyStore = loadKeyStore(KEYSTORE_PATH, KEYSTORE_PW);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PW.toCharArray());
        return kmf.getKeyManagers();
    }

    /**
     * Completely disable SSL verification (for testing only)
     */
    private void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = createTrustAllManagers();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            LOGGER.info("✓ SSL verification disabled successfully");
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
     * Step 1: Get JWT token from Keycloak using client credentials
     */
    private String getJWTTokenFromKeycloak() throws Exception {
        logSection("STEP 1: Getting JWT Token from Keycloak");
        logTokenRequestInfo();

        HttpURLConnection connection = createTokenConnection();
        try {
            sendTokenRequest(connection);
            return processTokenResponse(connection);
        } catch (Exception e) {
            LOGGER.error("Error during token retrieval: " + e.getMessage());
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    private void logTokenRequestInfo() {
        LOGGER.info("URL: " + KEYCLOAK_TOKEN_URL);
        LOGGER.info("Client ID: " + CLIENT_ID);
        LOGGER.info("Grant Type: client_credentials");
    }

    private HttpURLConnection createTokenConnection() throws Exception {
        URL url = new URL(KEYCLOAK_TOKEN_URL);
        HttpURLConnection connection = url.getProtocol().equalsIgnoreCase("https") ?
                (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();

        configureConnection(connection, "POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);
        return connection;
    }

    private void configureConnection(HttpURLConnection connection, String method) throws Exception {
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
    }

    private void sendTokenRequest(HttpURLConnection connection) throws Exception {
        String urlParameters = buildTokenRequestParameters();
        LOGGER.info("Request body (masked): client_id=" + CLIENT_ID + "&grant_type=client_credentials");

        try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
            wr.writeBytes(urlParameters);
            wr.flush();
        }
    }

    private String buildTokenRequestParameters() throws Exception {
        StringBuilder params = new StringBuilder();
        params.append("client_id=").append(URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8.toString()));
        params.append("&grant_type=").append(URLEncoder.encode("client_credentials", StandardCharsets.UTF_8.toString()));

        if (CLIENT_SECRET != null && !CLIENT_SECRET.trim().isEmpty()) {
            params.append("&client_secret=").append(URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8.toString()));
        }
        return params.toString();
    }

    private String processTokenResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        LOGGER.info("Response Code: " + responseCode);

        if (responseCode == 200 || responseCode == 201) {
            return extractTokenFromResponse(connection);
        } else {
            handleTokenError(connection, responseCode);
            throw new Exception("Failed to get JWT token. Response Code: " + responseCode);
        }
    }

    private String extractTokenFromResponse(HttpURLConnection connection) throws Exception {
        String response = readResponse(connection.getInputStream());
        JsonNode jsonResponse = objectMapper.readTree(response);

        if (!jsonResponse.has("access_token")) {
            LOGGER.error("Response does not contain access_token: " + response);
            throw new Exception("No access_token in response");
        }

        String accessToken = jsonResponse.get("access_token").asText();
        logTokenSuccess(jsonResponse, accessToken);
        return accessToken;
    }

    private void logTokenSuccess(JsonNode jsonResponse, String accessToken) {
        LOGGER.info("✓ Successfully retrieved JWT token");
        if (jsonResponse.has("token_type")) {
            LOGGER.info("  Token Type: " + jsonResponse.get("token_type").asText());
        }
        if (jsonResponse.has("expires_in")) {
            LOGGER.info("  Expires In: " + jsonResponse.get("expires_in").asInt() + " seconds");
        }
        LOGGER.info("  Token (first 50 chars): " +
                accessToken.substring(0, Math.min(50, accessToken.length())) + "...");
    }

    private void handleTokenError(HttpURLConnection connection, int responseCode) throws Exception {
        String errorResponse = readErrorResponse(connection);
        LOGGER.error("Failed to get JWT token. Response Code: " + responseCode);
        LOGGER.error("Error Response: " + errorResponse);
    }

    /**
     * Step 2: Get all producers and display their data providers
     */
    private void getAllProducers(String jwtToken) throws Exception {
        logSection("STEP 2: Getting All Producers");
        String apiUrl = FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id";
        LOGGER.info("URL: " + apiUrl);

        HttpURLConnection connection = createApiConnection(apiUrl, jwtToken);
        try {
            processProducerResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection createApiConnection(String apiUrl, String jwtToken) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = url.getProtocol().equalsIgnoreCase("https") ?
                (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();

        configureConnection(connection, "GET");
        connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
        return connection;
    }

    private void processProducerResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        LOGGER.info("Response Code: " + responseCode);

        if (responseCode == 200) {
            handleProducerSuccess(connection);
        } else {
            handleApiError(connection, responseCode, "producers");
        }
    }

    private void handleProducerSuccess(HttpURLConnection connection) throws Exception {
        String response = readResponse(connection.getInputStream());
        JsonNode jsonResponse = objectMapper.readTree(response);

        LOGGER.info("\n✓ Successfully retrieved producer configurations");
        logResponseStructure(jsonResponse);
        parseProducerResponse(jsonResponse);
    }

    private void parseProducerResponse(JsonNode jsonResponse) {
        if (jsonResponse.has("clientId")) {
            LOGGER.info("\nClient ID: " + jsonResponse.get("clientId").asText());
        }

        if (jsonResponse.has("producers") && jsonResponse.get("producers").isArray()) {
            processProducerArray((ArrayNode) jsonResponse.get("producers"));
        } else if (jsonResponse.isArray()) {
            processProducerArray((ArrayNode) jsonResponse);
        } else {
            handleUnexpectedProducerStructure(jsonResponse);
        }
    }

    private void processProducerArray(ArrayNode producers) {
        LOGGER.info("Total Producers Found: " + producers.size());
        LOGGER.info("\n=== PRODUCER LIST ===");

        int producerCount = 1;
        for (JsonNode producer : producers) {
            displayProducerInfo(producer, producerCount++);
        }
    }

    private void handleUnexpectedProducerStructure(JsonNode jsonResponse) {
        LOGGER.info("\n=== UNEXPECTED STRUCTURE ===");
        LOGGER.info("Response doesn't match expected structure.");
        LOGGER.info("Looking for direct producer fields...");
        displayProducerInfo(jsonResponse, 1);
    }

    /**
     * Step 3: Get all consumers and display their information
     */
    private void getAllConsumers(String jwtToken) throws Exception {
        logSection("STEP 3: Getting All Consumers");
        String apiUrl = FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id";
        LOGGER.info("URL: " + apiUrl);

        HttpURLConnection connection = createApiConnection(apiUrl, jwtToken);
        try {
            processConsumerResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private void processConsumerResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        LOGGER.info("Response Code: " + responseCode);

        if (responseCode == 200) {
            handleConsumerSuccess(connection);
        } else {
            handleApiError(connection, responseCode, "consumers");
        }
    }

    private void handleConsumerSuccess(HttpURLConnection connection) throws Exception {
        String response = readResponse(connection.getInputStream());
        JsonNode jsonResponse = objectMapper.readTree(response);

        LOGGER.info("\n✓ Successfully retrieved consumer configurations");
        logResponseStructure(jsonResponse);
        parseConsumerResponse(jsonResponse);
    }

    private void parseConsumerResponse(JsonNode jsonResponse) {
        if (jsonResponse.has("clientId")) {
            LOGGER.info("\nClient ID: " + jsonResponse.get("clientId").asText());
        }

        if (jsonResponse.has("consumers") && jsonResponse.get("consumers").isArray()) {
            processConsumerArray((ArrayNode) jsonResponse.get("consumers"));
        } else if (jsonResponse.isArray()) {
            processConsumerArray((ArrayNode) jsonResponse);
        } else {
            handleUnexpectedConsumerStructure(jsonResponse);
        }
    }

    private void processConsumerArray(ArrayNode consumers) {
        LOGGER.info("Total Consumers Found: " + consumers.size());
        LOGGER.info("\n=== CONSUMER LIST ===");

        int consumerCount = 1;
        for (JsonNode consumer : consumers) {
            displayConsumerInfo(consumer, consumerCount++);
        }
    }

    private void handleUnexpectedConsumerStructure(JsonNode jsonResponse) {
        LOGGER.info("\n=== UNEXPECTED STRUCTURE ===");
        LOGGER.info("Response doesn't match expected structure.");
        LOGGER.info("Looking for direct consumer fields...");
        displayConsumerInfo(jsonResponse, 1);
    }

    private void logResponseStructure(JsonNode jsonResponse) throws Exception {
        LOGGER.info("\nResponse structure received:");
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
        LOGGER.info(prettyJson.substring(0, Math.min(500, prettyJson.length())) + "...");
    }

    private void handleApiError(HttpURLConnection connection, int responseCode, String entityType) throws Exception {
        LOGGER.error("✗ Failed to get " + entityType + ". Response Code: " + responseCode);
        String errorResponse = readErrorResponse(connection);
        LOGGER.error("Error response: " + errorResponse);
    }

    /**
     * Display producer information including data providers
     */
    private void displayProducerInfo(JsonNode producer, int index) {
        LOGGER.info("\n--- Producer #" + index + " ---");
        displayProducerBasicInfo(producer);
        displayProducerDataProviders(producer);
    }

    private void displayProducerBasicInfo(JsonNode producer) {
        logFieldIfExists(producer, "name", "  Name: ");
        logFieldIfExists(producer, "description", "  Description: ");
        logBooleanFieldIfExists(producer, "active", "  Active: ");
        logFieldIfExists(producer, "host", "  Host: ");
        logIntFieldIfExists(producer, "port", "  Port: ");
        logBooleanFieldIfExists(producer, "tls", "  TLS Enabled: ");
        logFieldIfExists(producer, "idpClientId", "  IDP Client ID: ");
    }

    private void displayProducerDataProviders(JsonNode producer) {
        if (!producer.has("dataProviders")) {
            LOGGER.info("  Data Providers: None");
            return;
        }

        JsonNode dataProvidersNode = producer.get("dataProviders");
        if (!dataProvidersNode.isArray()) {
            LOGGER.info("  Data Providers: Not an array or empty");
            return;
        }

        ArrayNode dataProviders = (ArrayNode) dataProvidersNode;
        LOGGER.info("  Data Providers (" + dataProviders.size() + "):");
        for (JsonNode dp : dataProviders) {
            displayDataProvider(dp);
        }
    }

    private void displayDataProvider(JsonNode dp) {
        LOGGER.info("    ╔══ Data Provider ══╗");
        logFieldIfExists(dp, "name", "      Name: ");
        logFieldIfExists(dp, "topic", "      Topic: ");
        logFieldIfExists(dp, "description", "      Description: ");
        logBooleanFieldIfExists(dp, "active", "      Active: ");
        displayDataProviderConsumers(dp);
    }

    private void displayDataProviderConsumers(JsonNode dp) {
        if (!dp.has("consumers")) {
            return;
        }

        JsonNode consumersNode = dp.get("consumers");
        if (!consumersNode.isArray()) {
            return;
        }

        ArrayNode consumers = (ArrayNode) consumersNode;
        LOGGER.info("      Consumers (" + consumers.size() + "):");
        for (JsonNode consumer : consumers) {
            displayDataProviderConsumer(consumer);
        }
    }

    private void displayDataProviderConsumer(JsonNode consumer) {
        LOGGER.info("        • Consumer Details:");
        logFieldIfExists(consumer, "name", "          - Name: ");
        logFieldIfExists(consumer, "idpClientId", "          - IDP Client ID: ");
    }

    /**
     * Display consumer information
     */
    private void displayConsumerInfo(JsonNode consumer, int index) {
        LOGGER.info("\n--- Consumer #" + index + " ---");
        displayConsumerBasicInfo(consumer);
        displayConsumerSubscriptions(consumer);
    }

    private void displayConsumerBasicInfo(JsonNode consumer) {
        logFieldIfExists(consumer, "name", "  Name: ");
        logFieldIfExists(consumer, "description", "  Description: ");
        logBooleanFieldIfExists(consumer, "active", "  Active: ");
        logFieldIfExists(consumer, "host", "  Host: ");
        logIntFieldIfExists(consumer, "port", "  Port: ");
        logBooleanFieldIfExists(consumer, "tls", "  TLS Enabled: ");
        logFieldIfExists(consumer, "idpClientId", "  IDP Client ID: ");
    }

    private void displayConsumerSubscriptions(JsonNode consumer) {
        if (!consumer.has("subscribedDataProviders")) {
            LOGGER.info("  Subscribed Data Providers: None");
            return;
        }

        JsonNode subscribedProvidersNode = consumer.get("subscribedDataProviders");
        if (!subscribedProvidersNode.isArray()) {
            LOGGER.info("  Subscribed Data Providers: Not an array or empty");
            return;
        }

        ArrayNode subscribedProviders = (ArrayNode) subscribedProvidersNode;
        LOGGER.info("  Subscribed Data Providers (" + subscribedProviders.size() + "):");
        for (JsonNode provider : subscribedProviders) {
            displaySubscribedProvider(provider);
        }
    }

    private void displaySubscribedProvider(JsonNode provider) {
        LOGGER.info("    ╔══ Subscribed Provider ══╗");
        logFieldIfExists(provider, "name", "      Name: ");
        logFieldIfExists(provider, "topic", "      Topic: ");
        logFieldIfExists(provider, "producerName", "      Producer: ");
        logFieldIfExists(provider, "description", "      Description: ");
    }

    private void logFieldIfExists(JsonNode node, String fieldName, String prefix) {
        if (node.has(fieldName)) {
            LOGGER.info(prefix + node.get(fieldName).asText());
        }
    }

    private void logBooleanFieldIfExists(JsonNode node, String fieldName, String prefix) {
        if (node.has(fieldName)) {
            LOGGER.info(prefix + node.get(fieldName).asBoolean());
        }
    }

    private void logIntFieldIfExists(JsonNode node, String fieldName, String prefix) {
        if (node.has(fieldName)) {
            LOGGER.info(prefix + node.get(fieldName).asInt());
        }
    }

    private String readResponse(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private String readErrorResponse(HttpURLConnection connection) throws IOException {
        InputStream errorStream = connection.getErrorStream() != null ?
                connection.getErrorStream() : connection.getInputStream();
        return readResponse(errorStream);
    }

    /**
     * Run the complete connectivity test
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
            this.currentJwtToken = jwtToken;
            getAllProducers(jwtToken);
            getAllConsumers(jwtToken);
            return true;
        } catch (Exception e) {
            logTestError(e);
            return false;
        }
    }

    private void logTestHeader() {
        LOGGER.info("\n=====================================");
        LOGGER.info("CONFIGURATION ENDPOINT TEST");
        LOGGER.info("Testing Producer & Consumer Endpoints");
        LOGGER.info("=====================================");
    }

    private void logTestError(Exception e) {
        LOGGER.error("\n✗✗✗ TEST FAILED ✗✗✗");
        LOGGER.error("Error: " + e.getMessage());
    }

    private void logTestSummary(boolean testPassed, long duration) {
        logSection("TEST SUMMARY");
        LOGGER.info("Result: " + (testPassed ? "✓✓✓ PASSED" : "✗✗✗ FAILED"));
        LOGGER.info("Execution Time: " + duration + " ms");
        logTestedEndpoints();
        logExpectedStructure();
        LOGGER.info("=====================================");
    }

    private void logTestedEndpoints() {
        LOGGER.info("\nEndpoints tested:");
        LOGGER.info("- Producer endpoint: " + FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id");
        LOGGER.info("- Consumer endpoint: " + FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id");
    }

    private void logExpectedStructure() {
        LOGGER.info("\nExpected Response Structure:");
        LOGGER.info("{");
        LOGGER.info("  \"clientId\": \"FEDERATOR_XXX\",");
        LOGGER.info("  \"producers\": [ ... ] or \"consumers\": [ ... ]");
        LOGGER.info("}");
    }

    private void logSection(String sectionTitle) {
        LOGGER.info("\n========================================");
        LOGGER.info(sectionTitle);
        LOGGER.info("========================================");
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        logStartupInfo();

        if (handleCommandLineArgs(args)) {
            return;
        }

        ConfigurationClientTest2 test = new ConfigurationClientTest2();
        test.runTest();
    }

    private static void logStartupInfo() {
        LOGGER.info("Starting Configuration Client Test...");
        LOGGER.info("Java Version: " + System.getProperty("java.version"));
        LOGGER.info("Java Vendor: " + System.getProperty("java.vendor"));
    }

    private static boolean handleCommandLineArgs(String[] args) {
        if (args.length == 0) {
            return false;
        }

        if (args[0].equals("--help")) {
            showHelp();
            return true;
        } else if (args[0].equals("--debug-ssl")) {
            enableSSLDebug();
            return false;
        }
        return false;
    }

    private static void showHelp() {
        LOGGER.info("\nUsage: java ConfigurationClientTest [options]");
        LOGGER.info("Options:");
        LOGGER.info("  --help           Show this help message");
        LOGGER.info("  --debug-ssl      Enable SSL debugging");
        showTestDescription();
        showEndpointInfo();
    }

    private static void showTestDescription() {
        LOGGER.info("\nThis test will:");
        LOGGER.info("  1. Get JWT token from Keycloak");
        LOGGER.info("  2. List all producers and their data providers");
        LOGGER.info("  3. List all consumers and their subscriptions");
    }

    private static void showEndpointInfo() {
        LOGGER.info("\nEndpoints tested:");
        LOGGER.info("  - " + FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id");
        LOGGER.info("  - " + FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id");
    }

    private static void enableSSLDebug() {
        System.setProperty("javax.net.debug", "ssl,handshake,trustmanager");
        LOGGER.info("SSL debugging enabled");
    }
}