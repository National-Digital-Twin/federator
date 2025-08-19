package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

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

    // Configuration constants
    private static final String KEYCLOAK_TOKEN_URL = "https://localhost:8443/realms/management-node/protocol/openid-connect/token";
    private static final String FEDERATOR_BASE_URL = "https://localhost:8090";
    private static final String CLIENT_ID = "FEDERATOR_BCC";
    private static final String CLIENT_SECRET = ""; // Empty string or actual secret if required

    // SSL Configuration - Update these paths to your actual keystore locations
    private static final String TRUSTSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    private static final String KEYSTORE_PATH = "/home/vagrant/DBTWorkspace/federator/src/main/java/uk/gov/dbt/ndtp/federator/keys/keystore.jks";
    private static final String KEYSTORE_PASSWORD = "changeit";

    // Option to disable SSL verification (for testing only)
    private static final boolean DISABLE_SSL_VERIFICATION = false;

    private final ObjectMapper objectMapper;
    private String currentJwtToken;

    public ConfigurationClientTest2() {
        this.objectMapper = new ObjectMapper();

        // Setup SSL with better error handling
        if (DISABLE_SSL_VERIFICATION) {
            System.out.println("⚠️  WARNING: SSL verification is disabled. This should only be used for testing!");
            disableSSLVerification();
        } else {
            if (!setupSSLWithValidation()) {
                System.out.println("⚠️  SSL setup failed, you may want to enable DISABLE_SSL_VERIFICATION for testing");
            }
        }
    }

    /**
     * Verify SSL files exist and are readable before setting them
     */
    private boolean verifySSLFiles() {
        System.out.println("\n========================================");
        System.out.println("Verifying SSL Files");
        System.out.println("========================================");

        boolean allFilesValid = true;

        // Check truststore
        Path truststorePath = Paths.get(TRUSTSTORE_PATH);
        System.out.println("\nChecking Truststore:");
        System.out.println("  Path: " + TRUSTSTORE_PATH);

        if (!Files.exists(truststorePath)) {
            System.err.println("  ✗ Truststore file does NOT exist!");
            System.err.println("    Create it with: keytool -import -file server.crt -alias server -keystore " + TRUSTSTORE_PATH + " -storepass " + TRUSTSTORE_PASSWORD);
            allFilesValid = false;
        } else if (!Files.isReadable(truststorePath)) {
            System.err.println("  ✗ Truststore file is NOT readable!");
            allFilesValid = false;
        } else {
            System.out.println("  ✓ Truststore file exists and is readable");
            System.out.println("  File size: " + truststorePath.toFile().length() + " bytes");

            // Try to load the truststore to verify password
            try {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
                    trustStore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
                    System.out.println("  ✓ Truststore password is correct");
                    System.out.println("  Truststore type: " + trustStore.getType());
                    System.out.println("  Number of entries: " + trustStore.size());
                }
            } catch (Exception e) {
                System.err.println("  ✗ Failed to load truststore: " + e.getMessage());
                allFilesValid = false;
            }
        }

        // Check keystore (optional - only if client certificates are needed)
        Path keystorePath = Paths.get(KEYSTORE_PATH);
        System.out.println("\nChecking Keystore (optional for client certs):");
        System.out.println("  Path: " + KEYSTORE_PATH);

        if (!Files.exists(keystorePath)) {
            System.out.println("  ⚠️  Keystore file does not exist (OK if no client cert required)");
        } else if (!Files.isReadable(keystorePath)) {
            System.err.println("  ✗ Keystore file exists but is NOT readable!");
            allFilesValid = false;
        } else {
            System.out.println("  ✓ Keystore file exists and is readable");
            System.out.println("  File size: " + keystorePath.toFile().length() + " bytes");

            // Try to load the keystore to verify password
            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
                    keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
                    System.out.println("  ✓ Keystore password is correct");
                    System.out.println("  Keystore type: " + keyStore.getType());
                    System.out.println("  Number of entries: " + keyStore.size());
                }
            } catch (Exception e) {
                System.err.println("  ✗ Failed to load keystore: " + e.getMessage());
                allFilesValid = false;
            }
        }

        return allFilesValid;
    }

    /**
     * Setup SSL with proper validation and error handling
     */
    private boolean setupSSLWithValidation() {
        try {
            System.out.println("\n========================================");
            System.out.println("Setting up SSL with certificate verification");
            System.out.println("========================================");

            // First verify the files exist and are valid
            if (!verifySSLFiles()) {
                System.err.println("\n✗ SSL file verification failed!");
                System.err.println("Consider setting DISABLE_SSL_VERIFICATION = true for testing");
                return false;
            }

            // Set system properties first
            System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH);
            System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);

            // Only set keystore if it exists
            if (Files.exists(Paths.get(KEYSTORE_PATH))) {
                System.setProperty("javax.net.ssl.keyStore", KEYSTORE_PATH);
                System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);
            }

            // Load truststore
            System.out.println("\nLoading Truststore...");
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream trustStoreStream = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(trustStoreStream, TRUSTSTORE_PASSWORD.toCharArray());
            }

            // Create TrustManagerFactory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            KeyManager[] keyManagers = null;

            // Load keystore only if it exists
            if (Files.exists(Paths.get(KEYSTORE_PATH))) {
                System.out.println("Loading Keystore...");
                KeyStore keyStore = KeyStore.getInstance("JKS");
                try (FileInputStream keyStoreStream = new FileInputStream(KEYSTORE_PATH)) {
                    keyStore.load(keyStoreStream, KEYSTORE_PASSWORD.toCharArray());
                }

                // Create KeyManagerFactory
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            // Create SSL context
            System.out.println("Creating SSL Context...");
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, tmf.getTrustManagers(), new SecureRandom());

            // Set as default
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            System.out.println("\n✓ SSL setup completed successfully!");
            return true;

        } catch (Exception e) {
            System.err.println("\n✗ Failed to setup SSL: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Completely disable SSL verification (for testing only)
     */
    private void disableSSLVerification() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // Create all-trusting hostname verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            System.out.println("✓ SSL verification disabled successfully");

        } catch (Exception e) {
            System.err.println("Failed to disable SSL verification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Step 1: Get JWT token from Keycloak using client credentials
     */
    private String getJWTTokenFromKeycloak() throws Exception {
        System.out.println("\n========================================");
        System.out.println("STEP 1: Getting JWT Token from Keycloak");
        System.out.println("========================================");
        System.out.println("URL: " + KEYCLOAK_TOKEN_URL);
        System.out.println("Client ID: " + CLIENT_ID);
        System.out.println("Grant Type: client_credentials");

        URL url = new URL(KEYCLOAK_TOKEN_URL);
        HttpURLConnection connection;

        // Handle both HTTP and HTTPS
        if (url.getProtocol().equalsIgnoreCase("https")) {
            connection = (HttpsURLConnection) url.openConnection();
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }

        try {
            // Set request method and headers
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(30000); // 30 seconds

            // Build request body
            StringBuilder urlParameters = new StringBuilder();
            urlParameters.append("client_id=").append(URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8.toString()));
            urlParameters.append("&grant_type=").append(URLEncoder.encode("client_credentials", StandardCharsets.UTF_8.toString()));

            // Add client_secret if configured and not empty
            if (CLIENT_SECRET != null && !CLIENT_SECRET.trim().isEmpty()) {
                urlParameters.append("&client_secret=").append(URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8.toString()));
            }

            System.out.println("Request body (masked): client_id=" + CLIENT_ID + "&grant_type=client_credentials");

            // Send request
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(urlParameters.toString());
                wr.flush();
            }

            // Get response
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            if (responseCode == 200 || responseCode == 201) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse JSON response
                JsonNode jsonResponse = objectMapper.readTree(response.toString());

                if (!jsonResponse.has("access_token")) {
                    System.err.println("Response does not contain access_token: " + response.toString());
                    throw new Exception("No access_token in response");
                }

                String accessToken = jsonResponse.get("access_token").asText();

                System.out.println("✓ Successfully retrieved JWT token");
                if (jsonResponse.has("token_type")) {
                    System.out.println("  Token Type: " + jsonResponse.get("token_type").asText());
                }
                if (jsonResponse.has("expires_in")) {
                    System.out.println("  Expires In: " + jsonResponse.get("expires_in").asInt() + " seconds");
                }
                System.out.println("  Token (first 50 chars): " +
                        accessToken.substring(0, Math.min(50, accessToken.length())) + "...");

                return accessToken;

            } else {
                // Read error response
                BufferedReader errorReader;
                if (connection.getErrorStream() != null) {
                    errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                } else {
                    errorReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                }

                String inputLine;
                StringBuilder errorResponse = new StringBuilder();
                while ((inputLine = errorReader.readLine()) != null) {
                    errorResponse.append(inputLine);
                }
                errorReader.close();

                System.err.println("Failed to get JWT token. Response Code: " + responseCode);
                System.err.println("Error Response: " + errorResponse.toString());

                throw new Exception("Failed to get JWT token. Response Code: " + responseCode +
                        ", Response: " + errorResponse.toString());
            }

        } catch (Exception e) {
            System.err.println("Error during token retrieval: " + e.getMessage());
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Step 2: Get all producers and display their data providers
     */
    private void getAllProducers(String jwtToken) throws Exception {
        System.out.println("\n========================================");
        System.out.println("STEP 2: Getting All Producers");
        System.out.println("========================================");

        // Try without producer_id value to get all producers
        String apiUrl = FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id";
        System.out.println("URL: " + apiUrl);

        URL url = new URL(apiUrl);
        HttpURLConnection connection;

        if (url.getProtocol().equalsIgnoreCase("https")) {
            connection = (HttpsURLConnection) url.openConnection();
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }

        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse and display response
                JsonNode jsonResponse = objectMapper.readTree(response.toString());

                System.out.println("\n✓ Successfully retrieved producer configurations");

                // Debug: Print the structure we received
                System.out.println("\nResponse structure received:");
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
                System.out.println(prettyJson.substring(0, Math.min(500, prettyJson.length())) + "...");

                // Check for the expected structure: { "clientId": "...", "producers": [...] }
                if (jsonResponse.has("clientId")) {
                    System.out.println("\nClient ID: " + jsonResponse.get("clientId").asText());
                }

                if (jsonResponse.has("producers") && jsonResponse.get("producers").isArray()) {
                    ArrayNode producers = (ArrayNode) jsonResponse.get("producers");
                    System.out.println("Total Producers Found: " + producers.size());
                    System.out.println("\n=== PRODUCER LIST ===");

                    int producerCount = 1;
                    for (JsonNode producer : producers) {
                        displayProducerInfo(producer, producerCount++);
                    }
                } else if (jsonResponse.isArray()) {
                    // Handle if response is directly an array
                    ArrayNode producers = (ArrayNode) jsonResponse;
                    System.out.println("Total Producers Found: " + producers.size());
                    System.out.println("\n=== PRODUCER LIST ===");

                    int producerCount = 1;
                    for (JsonNode producer : producers) {
                        displayProducerInfo(producer, producerCount++);
                    }
                } else {
                    // Single producer or unexpected structure
                    System.out.println("\n=== UNEXPECTED STRUCTURE ===");
                    System.out.println("Response doesn't match expected structure.");
                    System.out.println("Looking for direct producer fields...");
                    displayProducerInfo(jsonResponse, 1);
                }

            } else {
                System.err.println("✗ Failed to get producers. Response Code: " + responseCode);

                // Try to read error response
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream() != null ?
                                connection.getErrorStream() : connection.getInputStream())
                );
                String line;
                StringBuilder errorResponse = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                System.err.println("Error response: " + errorResponse.toString());
            }

        } finally {
            connection.disconnect();
        }
    }

    /**
     * Step 3: Get all consumers and display their information
     */
    private void getAllConsumers(String jwtToken) throws Exception {
        System.out.println("\n========================================");
        System.out.println("STEP 3: Getting All Consumers");
        System.out.println("========================================");

        // Try without consumer_id value to get all consumers
        String apiUrl = FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id";
        System.out.println("URL: " + apiUrl);

        URL url = new URL(apiUrl);
        HttpURLConnection connection;

        if (url.getProtocol().equalsIgnoreCase("https")) {
            connection = (HttpsURLConnection) url.openConnection();
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }

        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse and display response
                JsonNode jsonResponse = objectMapper.readTree(response.toString());

                System.out.println("\n✓ Successfully retrieved consumer configurations");

                // Debug: Print the structure we received
                System.out.println("\nResponse structure received:");
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
                System.out.println(prettyJson.substring(0, Math.min(500, prettyJson.length())) + "...");

                // Check for the expected structure: { "clientId": "...", "consumers": [...] }
                if (jsonResponse.has("clientId")) {
                    System.out.println("\nClient ID: " + jsonResponse.get("clientId").asText());
                }

                if (jsonResponse.has("consumers") && jsonResponse.get("consumers").isArray()) {
                    ArrayNode consumers = (ArrayNode) jsonResponse.get("consumers");
                    System.out.println("Total Consumers Found: " + consumers.size());
                    System.out.println("\n=== CONSUMER LIST ===");

                    int consumerCount = 1;
                    for (JsonNode consumer : consumers) {
                        displayConsumerInfo(consumer, consumerCount++);
                    }
                } else if (jsonResponse.isArray()) {
                    // Handle if response is directly an array
                    ArrayNode consumers = (ArrayNode) jsonResponse;
                    System.out.println("Total Consumers Found: " + consumers.size());
                    System.out.println("\n=== CONSUMER LIST ===");

                    int consumerCount = 1;
                    for (JsonNode consumer : consumers) {
                        displayConsumerInfo(consumer, consumerCount++);
                    }
                } else {
                    // Single consumer or unexpected structure
                    System.out.println("\n=== UNEXPECTED STRUCTURE ===");
                    System.out.println("Response doesn't match expected structure.");
                    System.out.println("Looking for direct consumer fields...");
                    displayConsumerInfo(jsonResponse, 1);
                }

            } else {
                System.err.println("✗ Failed to get consumers. Response Code: " + responseCode);

                // Try to read error response
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream() != null ?
                                connection.getErrorStream() : connection.getInputStream())
                );
                String line;
                StringBuilder errorResponse = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                System.err.println("Error response: " + errorResponse.toString());
            }

        } finally {
            connection.disconnect();
        }
    }

    /**
     * Display producer information including data providers
     */
    private void displayProducerInfo(JsonNode producer, int index) {
        System.out.println("\n--- Producer #" + index + " ---");

        // Display all producer fields
        if (producer.has("name")) {
            System.out.println("  Name: " + producer.get("name").asText());
        }
        if (producer.has("description")) {
            System.out.println("  Description: " + producer.get("description").asText());
        }
        if (producer.has("active")) {
            System.out.println("  Active: " + producer.get("active").asBoolean());
        }
        if (producer.has("host")) {
            System.out.println("  Host: " + producer.get("host").asText());
        }
        if (producer.has("port")) {
            System.out.println("  Port: " + producer.get("port").asInt());
        }
        if (producer.has("tls")) {
            System.out.println("  TLS Enabled: " + producer.get("tls").asBoolean());
        }
        if (producer.has("idpClientId")) {
            System.out.println("  IDP Client ID: " + producer.get("idpClientId").asText());
        }

        // Display data providers
        if (producer.has("dataProviders")) {
            JsonNode dataProvidersNode = producer.get("dataProviders");
            if (dataProvidersNode.isArray()) {
                ArrayNode dataProviders = (ArrayNode) dataProvidersNode;
                System.out.println("  Data Providers (" + dataProviders.size() + "):");

                for (JsonNode dp : dataProviders) {
                    System.out.println("    ═══ Data Provider ═══");
                    if (dp.has("name")) {
                        System.out.println("      Name: " + dp.get("name").asText());
                    }
                    if (dp.has("topic")) {
                        System.out.println("      Topic: " + dp.get("topic").asText());
                    }
                    if (dp.has("description")) {
                        System.out.println("      Description: " + dp.get("description").asText());
                    }
                    if (dp.has("active")) {
                        System.out.println("      Active: " + dp.get("active").asBoolean());
                    }

                    // Display consumers for this data provider
                    if (dp.has("consumers")) {
                        JsonNode consumersNode = dp.get("consumers");
                        if (consumersNode.isArray()) {
                            ArrayNode consumers = (ArrayNode) consumersNode;
                            System.out.println("      Consumers (" + consumers.size() + "):");
                            for (JsonNode consumer : consumers) {
                                System.out.println("        • Consumer Details:");
                                if (consumer.has("name")) {
                                    System.out.println("          - Name: " + consumer.get("name").asText());
                                }
                                if (consumer.has("idpClientId")) {
                                    System.out.println("          - IDP Client ID: " + consumer.get("idpClientId").asText());
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println("  Data Providers: Not an array or empty");
            }
        } else {
            System.out.println("  Data Providers: None");
        }
    }

    /**
     * Display consumer information
     */
    private void displayConsumerInfo(JsonNode consumer, int index) {
        System.out.println("\n--- Consumer #" + index + " ---");

        // Display all consumer fields
        if (consumer.has("name")) {
            System.out.println("  Name: " + consumer.get("name").asText());
        }
        if (consumer.has("description")) {
            System.out.println("  Description: " + consumer.get("description").asText());
        }
        if (consumer.has("active")) {
            System.out.println("  Active: " + consumer.get("active").asBoolean());
        }
        if (consumer.has("host")) {
            System.out.println("  Host: " + consumer.get("host").asText());
        }
        if (consumer.has("port")) {
            System.out.println("  Port: " + consumer.get("port").asInt());
        }
        if (consumer.has("tls")) {
            System.out.println("  TLS Enabled: " + consumer.get("tls").asBoolean());
        }
        if (consumer.has("idpClientId")) {
            System.out.println("  IDP Client ID: " + consumer.get("idpClientId").asText());
        }

        // Display subscribed data providers
        if (consumer.has("subscribedDataProviders")) {
            JsonNode subscribedProvidersNode = consumer.get("subscribedDataProviders");
            if (subscribedProvidersNode.isArray()) {
                ArrayNode subscribedProviders = (ArrayNode) subscribedProvidersNode;
                System.out.println("  Subscribed Data Providers (" + subscribedProviders.size() + "):");

                for (JsonNode provider : subscribedProviders) {
                    System.out.println("    ═══ Subscribed Provider ═══");
                    if (provider.has("name")) {
                        System.out.println("      Name: " + provider.get("name").asText());
                    }
                    if (provider.has("topic")) {
                        System.out.println("      Topic: " + provider.get("topic").asText());
                    }
                    if (provider.has("producerName")) {
                        System.out.println("      Producer: " + provider.get("producerName").asText());
                    }
                    if (provider.has("description")) {
                        System.out.println("      Description: " + provider.get("description").asText());
                    }
                }
            } else {
                System.out.println("  Subscribed Data Providers: Not an array or empty");
            }
        } else {
            System.out.println("  Subscribed Data Providers: None");
        }
    }

    /**
     * Run the complete connectivity test
     */
    public void runTest() {
        System.out.println("\n=====================================");
        System.out.println("CONFIGURATION ENDPOINT TEST");
        System.out.println("Testing Producer & Consumer Endpoints");
        System.out.println("=====================================");

        long startTime = System.currentTimeMillis();
        boolean testPassed = false;

        try {
            // Step 1: Get JWT token from Keycloak
            String jwtToken = getJWTTokenFromKeycloak();
            this.currentJwtToken = jwtToken;

            // Step 2: Get all producers
            getAllProducers(jwtToken);

            // Step 3: Get all consumers
            getAllConsumers(jwtToken);

            testPassed = true;

        } catch (Exception e) {
            System.err.println("\n✗✗✗ TEST FAILED ✗✗✗");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        long duration = System.currentTimeMillis() - startTime;

        // Print summary of findings
        System.out.println("\n=====================================");
        System.out.println("TEST SUMMARY");
        System.out.println("=====================================");
        System.out.println("Result: " + (testPassed ? "✓✓✓ PASSED" : "✗✗✗ FAILED"));
        System.out.println("Execution Time: " + duration + " ms");
        System.out.println("\nEndpoints tested:");
        System.out.println("- Producer endpoint: " + FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id");
        System.out.println("- Consumer endpoint: " + FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id");
        System.out.println("\nExpected Response Structure:");
        System.out.println("{");
        System.out.println("  \"clientId\": \"FEDERATOR_XXX\",");
        System.out.println("  \"producers\": [ ... ] or \"consumers\": [ ... ]");
        System.out.println("}");
        System.out.println("=====================================");

        // Exit with appropriate code
        System.exit(testPassed ? 0 : 1);
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        System.out.println("Starting Configuration Client Test...");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Java Vendor: " + System.getProperty("java.vendor"));

        // Check for command line arguments
        if (args.length > 0) {
            if (args[0].equals("--help")) {
                System.out.println("\nUsage: java ConfigurationClientTest [options]");
                System.out.println("Options:");
                System.out.println("  --help           Show this help message");
                System.out.println("  --debug-ssl      Enable SSL debugging");
                System.out.println("\nThis test will:");
                System.out.println("  1. Get JWT token from Keycloak");
                System.out.println("  2. List all producers and their data providers");
                System.out.println("  3. List all consumers and their subscriptions");
                System.out.println("\nEndpoints tested:");
                System.out.println("  - " + FEDERATOR_BASE_URL + "/api/v1/configuration/producer?producer_id");
                System.out.println("  - " + FEDERATOR_BASE_URL + "/api/v1/configuration/consumer?consumer_id");
                return;
            } else if (args[0].equals("--debug-ssl")) {
                System.setProperty("javax.net.debug", "ssl,handshake,trustmanager");
                System.out.println("SSL debugging enabled");
            }
        }

        ConfigurationClientTest2 test = new ConfigurationClientTest2();
        test.runTest();
    }
}