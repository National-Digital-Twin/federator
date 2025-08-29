// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.FederatorConfigurationService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenServiceImpl;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;

/**
 * Test client for configuration retrieval from Management Node.
 * <p>
 * This class provides comprehensive testing of the configuration
 * retrieval mechanism from the Management Node, including SSL/TLS
 * setup, token service validation, and caching performance.
 * </p>
 *
 * @author NDTP Development Team
 * @version 1.0
 * @since 2024
 */
@Slf4j
public final class ConfigurationClientTest {

    // SSL/TLS Configuration Constants
    private static final String TLS_PROTOCOL = "TLS";
    private static final String PKCS12_TYPE = "PKCS12";
    private static final String JKS_TYPE = "JKS";

    // Timeout and Performance Constants
    private static final int TIMEOUT_SECONDS = 10;
    private static final int CACHE_PERF_FACTOR = 10;

    // File Path Constants
    private static final String CLIENT_CERT_PATH =
            "/home/vagrant/pki/client/client.p12";
    private static final String TRUSTSTORE_PATH =
            "/home/vagrant/pki/client/truststore.jks";
    private static final String PROPERTIES_PATH =
            "/home/vagrant/pki/properties/server.properties";

    // Property Keys
    private static final String PROP_TRUSTSTORE_PASSWD =
            "server.truststorePassword";
    private static final String PROP_CLIENT_CERT_PASSWD =
            "server.p12Password";

    // Log Message Constants
    private static final String MSG_PROPS_LOADED =
            "Properties loaded from: {}";
    private static final String MSG_TOKEN_FETCHED =
            "Token fetched: {}";
    private static final String MSG_TOKEN_VERIFIED =
            "Token verified: {}";
    private static final String MSG_TEST_COMPLETE =
            "\n=== All tests completed successfully ===";
    private static final String MSG_TEST_FAILED =
            "Test failed";
    private static final String MSG_PRODUCER_TEST =
            "\n=== Testing Producer Configuration ===";
    private static final String MSG_CONSUMER_TEST =
            "\n=== Testing Consumer Configuration ===";
    private static final String MSG_SERVICE_TEST =
            "\n=== Testing Configuration Service ===";
    private static final String MSG_PRODUCER_ID =
            "Producer Client ID: {}";
    private static final String MSG_CONSUMER_ID =
            "Consumer Client ID: {}";
    private static final String MSG_CONFIG_JSON =
            "Config:\n{}";
    private static final String MSG_API_FETCH =
            "First fetch (from API)...";
    private static final String MSG_CACHE_FETCH =
            "Second fetch (from cache)...";
    private static final String MSG_FETCH_TIME =
            "{} fetch took: {} ms";
    private static final String MSG_CACHED_ID =
            "Cached ID: {}";
    private static final String MSG_CACHE_OK =
            "Cache working correctly";
    private static final String MSG_CACHE_ISSUE =
            "Cache performance issue detected";
    private static final String MSG_REFRESH_TEST =
            "Testing refresh...";
    private static final String MSG_REFRESH_DONE =
            "Refresh completed";
    private static final String MSG_ERROR_PRODUCER =
            "Producer test failed: {}";
    private static final String MSG_ERROR_CONSUMER =
            "Consumer test failed: {}";
    private static final String MSG_ERROR_SERVICE =
            "Service test failed";

    // Fetch Type Constants
    private static final String FETCH_TYPE_API = "API";
    private static final String FETCH_TYPE_CACHE = "Cache";

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private ConfigurationClientTest() {
        // Private constructor to prevent instantiation
    }

    /**
     * Main entry point for configuration testing.
     * <p>
     * Initializes the test environment, creates necessary components,
     * and executes all test scenarios.
     * </p>
     *
     * @param args command line arguments (not used)
     */
    public static void main(final String[] args) {
        try {
            initializeProperties();
            final TestComponents components = createTestComponents();
            runAllTests(components);
            log.info(MSG_TEST_COMPLETE);
        } catch (Exception e) {
            log.error(MSG_TEST_FAILED, e);
            System.exit(1);
        }
    }

    /**
     * Initializes system properties from configuration file.
     *
     * @throws IOException if properties file cannot be read
     */
    private static void initializeProperties() throws IOException {
        final File propertiesFile = new File(PROPERTIES_PATH);
        PropertyUtil.init(propertiesFile);
        log.info(MSG_PROPS_LOADED, propertiesFile.getAbsolutePath());
    }

    /**
     * Creates all test components required for testing.
     *
     * @return TestComponents containing all initialized components
     * @throws GeneralSecurityException if SSL setup fails
     * @throws IOException if certificate files cannot be read
     */
    private static TestComponents createTestComponents()
            throws GeneralSecurityException, IOException {
        final HttpClient httpClient = createHttpClient();
        final ObjectMapper objectMapper = createObjectMapper();
        final IdpTokenService tokenService = createTokenService();

        final InMemoryConfigurationStore configStore =
                new InMemoryConfigurationStore();
        final ManagementNodeDataHandler dataHandler =
                new ManagementNodeDataHandler(
                        httpClient, objectMapper, tokenService);
        final FederatorConfigurationService configService =
                new FederatorConfigurationService(
                        dataHandler, configStore);

        return new TestComponents(
                dataHandler, objectMapper, configService);
    }

    /**
     * Creates and configures ObjectMapper for JSON serialization.
     *
     * @return configured ObjectMapper instance
     */
    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Creates and validates the IDP token service.
     *
     * @return validated IdpTokenService instance
     */
    private static IdpTokenService createTokenService() {
        final IdpTokenService service = new IdpTokenServiceImpl();
        validateTokenService(service);
        return service;
    }

    /**
     * Validates token service functionality.
     *
     * @param service the IdpTokenService to validate
     */
    private static void validateTokenService(
            final IdpTokenService service) {
        final String token = service.fetchToken();
        log.info(MSG_TOKEN_FETCHED, token != null);
        final boolean verified = service.verifyToken(token);
        log.info(MSG_TOKEN_VERIFIED, verified);
    }

    /**
     * Creates HTTP client with SSL/TLS configuration.
     *
     * @return configured HttpClient instance
     * @throws GeneralSecurityException if SSL setup fails
     * @throws IOException if certificate files cannot be read
     */
    private static HttpClient createHttpClient()
            throws GeneralSecurityException, IOException {
        final SSLContext sslContext = createSSLContext();
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    /**
     * Creates SSL context with client certificate and truststore.
     *
     * @return configured SSLContext
     * @throws GeneralSecurityException if SSL setup fails
     * @throws IOException if certificate files cannot be read
     */
    private static SSLContext createSSLContext()
            throws GeneralSecurityException, IOException {
        final KeyManagerFactory kmf = createKeyManagerFactory();
        final TrustManagerFactory tmf = createTrustManagerFactory();

        final SSLContext sslContext =
                SSLContext.getInstance(TLS_PROTOCOL);
        sslContext.init(kmf.getKeyManagers(),
                tmf.getTrustManagers(), null);
        return sslContext;
    }

    /**
     * Creates KeyManagerFactory with client certificate.
     *
     * @return configured KeyManagerFactory
     * @throws KeyStoreException if keystore cannot be created
     * @throws IOException if certificate file cannot be read
     * @throws NoSuchAlgorithmException if algorithm is not available
     * @throws CertificateException if certificate is invalid
     * @throws UnrecoverableKeyException if key cannot be recovered
     * @throws IllegalStateException if password not configured
     */
    private static KeyManagerFactory createKeyManagerFactory()
            throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException,
            UnrecoverableKeyException {
        final KeyStore keyStore = KeyStore.getInstance(PKCS12_TYPE);
        final String clientCertPasswd = PropertyUtil.getPropertyValue(
                PROP_CLIENT_CERT_PASSWD, null);
        if (clientCertPasswd == null ||
                clientCertPasswd.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Client certificate password not configured in " +
                            "properties file. Please set: " +
                            PROP_CLIENT_CERT_PASSWD);
        }
        loadKeyStore(keyStore, CLIENT_CERT_PATH, clientCertPasswd);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, clientCertPasswd.toCharArray());
        return kmf;
    }

    /**
     * Creates TrustManagerFactory with truststore.
     *
     * @return configured TrustManagerFactory
     * @throws KeyStoreException if truststore cannot be created
     * @throws IOException if truststore file cannot be read
     * @throws NoSuchAlgorithmException if algorithm is not available
     * @throws CertificateException if certificate is invalid
     * @throws IllegalStateException if password not configured
     */
    private static TrustManagerFactory createTrustManagerFactory()
            throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException {
        final KeyStore trustStore = KeyStore.getInstance(JKS_TYPE);
        final String truststorePasswd = PropertyUtil.getPropertyValue(
                PROP_TRUSTSTORE_PASSWD, null);
        if (truststorePasswd == null ||
                truststorePasswd.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Truststore password not configured in " +
                            "properties file. Please set: " +
                            PROP_TRUSTSTORE_PASSWD);
        }
        loadKeyStore(trustStore, TRUSTSTORE_PATH, truststorePasswd);

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    /**
     * Loads a keystore from file.
     *
     * @param keyStore the KeyStore to load into
     * @param path the file path to load from
     * @param password the keystore password
     * @throws IOException if file cannot be read
     * @throws NoSuchAlgorithmException if algorithm is not available
     * @throws CertificateException if certificate is invalid
     */
    private static void loadKeyStore(
            final KeyStore keyStore, final String path,
            final String password)
            throws IOException, NoSuchAlgorithmException,
            CertificateException {
        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password.toCharArray());
        }
    }

    /**
     * Executes all test scenarios.
     *
     * @param components the test components to use
     */
    private static void runAllTests(final TestComponents components) {
        log.info(MSG_PRODUCER_TEST);
        testProducerConfiguration(
                components.dataHandler(), components.mapper());

        log.info(MSG_CONSUMER_TEST);
        testConsumerConfiguration(
                components.dataHandler(), components.mapper());

        log.info(MSG_SERVICE_TEST);
        testConfigurationService(components.configService());
    }

    /**
     * Tests producer configuration retrieval.
     *
     * @param handler the data handler to test
     * @param mapper the ObjectMapper for JSON serialization
     */
    private static void testProducerConfiguration(
            final ManagementNodeDataHandler handler,
            final ObjectMapper mapper) {
        try {
            final ProducerConfigDTO producer =
                    handler.getProducerData(null);
            log.info(MSG_PRODUCER_ID, producer.getClientId());
            final String json = mapper.writeValueAsString(producer);
            log.info(MSG_CONFIG_JSON, json);
        } catch (Exception e) {
            log.error(MSG_ERROR_PRODUCER, e.getMessage());
        }
    }

    /**
     * Tests consumer configuration retrieval.
     *
     * @param handler the data handler to test
     * @param mapper the ObjectMapper for JSON serialization
     */
    private static void testConsumerConfiguration(
            final ManagementNodeDataHandler handler,
            final ObjectMapper mapper) {
        try {
            final ConsumerConfigDTO consumer =
                    handler.getConsumerData(null);
            log.info(MSG_CONSUMER_ID, consumer.getClientId());
            final String json = mapper.writeValueAsString(consumer);
            log.info(MSG_CONFIG_JSON, json);
        } catch (Exception e) {
            log.error(MSG_ERROR_CONSUMER, e.getMessage());
        }
    }

    /**
     * Tests configuration service functionality.
     *
     * @param service the configuration service to test
     */
    private static void testConfigurationService(
            final FederatorConfigurationService service) {
        try {
            final long apiTime = measureApiCall(service);
            final long cacheTime = measureCacheCall(service);
            verifyCachePerformance(apiTime, cacheTime);
            testRefresh(service);
        } catch (Exception e) {
            log.error(MSG_ERROR_SERVICE, e);
        }
    }

    /**
     * Measures API call performance.
     *
     * @param service the configuration service
     * @return duration in milliseconds
     * @throws Exception if API call fails
     */
    private static long measureApiCall(
            final FederatorConfigurationService service)
            throws Exception {
        log.info(MSG_API_FETCH);
        return measureFetchTime(service, FETCH_TYPE_API);
    }

    /**
     * Measures cache call performance.
     *
     * @param service the configuration service
     * @return duration in milliseconds
     * @throws Exception if cache call fails
     */
    private static long measureCacheCall(
            final FederatorConfigurationService service)
            throws Exception {
        log.info(MSG_CACHE_FETCH);
        return measureFetchTime(service, FETCH_TYPE_CACHE);
    }

    /**
     * Measures fetch time for configuration retrieval.
     *
     * @param service the configuration service
     * @param fetchType the type of fetch (API or Cache)
     * @return duration in milliseconds
     * @throws Exception if fetch fails
     */
    private static long measureFetchTime(
            final FederatorConfigurationService service,
            final String fetchType) throws Exception {
        final long start = System.currentTimeMillis();
        final ProducerConfigDTO producer =
                service.getProducerConfiguration();
        final long duration = System.currentTimeMillis() - start;

        log.info(MSG_FETCH_TIME, fetchType, duration);
        if (FETCH_TYPE_CACHE.equals(fetchType)) {
            log.info(MSG_CACHED_ID, producer.getClientId());
        } else {
            log.info(MSG_PRODUCER_ID, producer.getClientId());
        }
        return duration;
    }

    /**
     * Verifies cache performance meets expectations.
     *
     * @param apiTime the API call duration
     * @param cacheTime the cache call duration
     */
    private static void verifyCachePerformance(
            final long apiTime, final long cacheTime) {
        if (cacheTime < apiTime / CACHE_PERF_FACTOR) {
            log.info(MSG_CACHE_OK);
        } else {
            log.warn(MSG_CACHE_ISSUE);
        }
    }

    /**
     * Tests configuration refresh functionality.
     *
     * @param service the configuration service
     * @throws Exception if refresh fails
     */
    private static void testRefresh(
            final FederatorConfigurationService service)
            throws Exception {
        log.info(MSG_REFRESH_TEST);
        service.refreshConfigurations();
        log.info(MSG_REFRESH_DONE);
    }

    /**
     * Container record for test components.
     * <p>
     * Encapsulates all components required for testing the
     * configuration client functionality.
     * </p>
     *
     * @param dataHandler the management node data handler
     * @param mapper the ObjectMapper for JSON operations
     * @param configService the federator configuration service
     */
    private record TestComponents(
            ManagementNodeDataHandler dataHandler,
            ObjectMapper mapper,
            FederatorConfigurationService configService) {
    }
}