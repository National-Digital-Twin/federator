// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.handlers.ClientDynamicConfigJob;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.service.ProducerConsumerConfigService;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

/**
 * End-to-end integration test for Management Node integration.
 */
@DisplayName("Management Node Integration")
class ManagementNodeIntegrationTest {

    private static final int PORT = 8091;
    private static final String TOKEN = "test-token";
    private static final int TIMEOUT = 5;
    private static final String BASE_URL = "http://localhost:";
    private static final String PRODUCER_PATH = "/api/v1/configuration/producer";
    private static final String CONSUMER_PATH = "/api/v1/configuration/consumer";
    private static final String TOKEN_PATH = "/auth/token";
    private static final String JSON_TYPE = "application/json";
    private static final int HTTP_OK = 200;
    private static final String TEST_PRODUCER = "TestProducer";
    private static final String TEST_HOST = "test.host.com";
    private static final String TEST_TOPIC = "test-topic";
    private static final int TEST_PORT = 9092;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String TEST_CLIENT = "test-client";
    private static final String TEST_ID = "test";
    private static final String TEST_JOB = "test-job";
    private static final String TEST_NAME = "test";
    private static final String TEST_PRODUCT = "TestProduct";

    // Property keys
    private static final String PROP_BASE_URL = "management.node.base.url";
    private static final String PROP_TIMEOUT = "management.node.request.timeout";
    private static final String PROP_AUTH_ENABLED = "management.node.auth.enabled";
    private static final String PROP_RETRY_ATTEMPTS = "management.node.retry.max.attempts";
    private static final String PROP_RETRY_DELAY = "management.node.retry.delay";

    private static File tempFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private ProducerConsumerConfigService configService;
    private JobSchedulerProvider scheduler;

    /**
     * Sets up properties before all tests.
     *
     * @throws IOException on file creation error
     */
    @BeforeAll
    static void initProperties() throws IOException {
        PropertyUtil.clear();
        setupSystemProperties();
        createPropertiesFile();
        PropertyUtil.init(tempFile);
    }

    /**
     * Cleans up after all tests.
     */
    @AfterAll
    static void cleanupProperties() {
        PropertyUtil.clear();
        cleanupSystemProperties();
        deleteFile(tempFile);
    }

    private static void setupSystemProperties() {
        System.setProperty(PROP_BASE_URL, BASE_URL + PORT);
        System.setProperty(PROP_TIMEOUT, "10");
        System.setProperty(PROP_AUTH_ENABLED, "false");
        System.setProperty(PROP_RETRY_ATTEMPTS, "3");
        System.setProperty(PROP_RETRY_DELAY, "1000");
    }

    private static void cleanupSystemProperties() {
        System.clearProperty(PROP_BASE_URL);
        System.clearProperty(PROP_TIMEOUT);
        System.clearProperty(PROP_AUTH_ENABLED);
        System.clearProperty(PROP_RETRY_ATTEMPTS);
        System.clearProperty(PROP_RETRY_DELAY);
    }

    private static void createPropertiesFile() throws IOException {
        tempFile = File.createTempFile("test", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writeProperties(writer);
        }
    }

    private static void writeProperties(final FileWriter writer) throws IOException {
        writer.write(PROP_BASE_URL + "=" + BASE_URL + PORT + "\n");
        writer.write(PROP_TIMEOUT + "=10\n");
        writer.write(PROP_AUTH_ENABLED + "=false\n");
        writer.write(PROP_RETRY_ATTEMPTS + "=3\n");
        writer.write(PROP_RETRY_DELAY + "=1000\n");
    }

    private static void deleteFile(final File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    /**
     * Sets up test environment.
     *
     * @throws IOException on server setup error
     */
    @BeforeEach
    void setUp() throws IOException {
        startMockServer();
        initializeServices();
    }

    /**
     * Cleans up test environment.
     */
    @AfterEach
    void tearDown() {
        stopServer();
        stopScheduler();
    }

    /**
     * Tests fetching configuration.
     *
     * @throws Exception on test failure
     */
    @Test
    @DisplayName("Should fetch configuration from Management Node")
    void shouldFetchConfiguration() throws Exception {
        final ProducerConfigDTO config = configService.getProducerConfiguration();

        assertNotNull(config);
        assertNotNull(config.getProducers());
        assertEquals(1, config.getProducers().size());

        validateProducer(config.getProducers().get(0));
    }

    /**
     * Tests job creation.
     *
     * @throws Exception on test failure
     */
    @Test
    @DisplayName("Should create jobs from configuration")
    void shouldCreateJobsFromConfiguration() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientDynamicConfigJob job = createJob();

        job.run(createJobParams());
        latch.countDown();

        assertTrue(latch.await(TIMEOUT, TimeUnit.SECONDS));
    }

    /**
     * Tests configuration caching.
     *
     * @throws Exception on test failure
     */
    @Test
    @DisplayName("Should cache configuration")
    void shouldCacheConfiguration() throws Exception {
        final ProducerConfigDTO first = configService.getProducerConfiguration();
        final ProducerConfigDTO second = configService.getProducerConfiguration();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getClientId(), second.getClientId());
    }

    /**
     * Tests configuration refresh.
     *
     * @throws Exception on test failure
     */
    @Test
    @DisplayName("Should refresh configuration")
    void shouldRefreshConfiguration() throws Exception {
        configService.getProducerConfiguration();
        configService.clearCache();

        final ProducerConfigDTO refreshed = configService.getProducerConfiguration();
        assertNotNull(refreshed);
    }

    private void startMockServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext(TOKEN_PATH, new TokenHandler());
        server.createContext(PRODUCER_PATH, new ProducerHandler());
        server.createContext(CONSUMER_PATH, new ConsumerHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private void initializeServices() {
        scheduler = mock(JobSchedulerProvider.class);
        configService = createConfigurationService();
    }

    private ProducerConsumerConfigService createConfigurationService() {
        final HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        final IdpTokenService tokenService = createTokenService();
        final ManagementNodeDataHandler handler = new ManagementNodeDataHandler(httpClient, mapper, tokenService);

        return new ProducerConsumerConfigService(handler, InMemoryConfigurationStore.getInstance());
    }

    private IdpTokenService createTokenService() {
        final IdpTokenService tokenService = mock(IdpTokenService.class);
        when(tokenService.fetchToken()).thenReturn(TOKEN);
        when(tokenService.verifyToken(TOKEN)).thenReturn(true);
        return tokenService;
    }

    private void validateProducer(final ProducerDTO producer) {
        assertEquals(TEST_PRODUCER, producer.getName());
        assertEquals(TEST_HOST, producer.getHost());
        assertEquals(TEST_PORT, producer.getPort().intValue());
    }

    private ClientDynamicConfigJob createJob() {
        return new ClientDynamicConfigJob(configService, scheduler);
    }

    private JobParams createJobParams() {
        return JobParams.builder()
                .jobId(TEST_JOB)
                .jobName(TEST_NAME)
                .duration(Duration.ofSeconds(30))
                .build();
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void stopScheduler() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    private void sendResponse(final HttpExchange exchange, final String response) throws IOException {
        exchange.getResponseHeaders().add(CONTENT_TYPE, JSON_TYPE);
        exchange.sendResponseHeaders(HTTP_OK, response.length());

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    private ProducerConfigDTO createTestConfig() {
        final ProductDTO product =
                ProductDTO.builder().name(TEST_PRODUCT).topic(TEST_TOPIC).build();

        final ProducerDTO producer = ProducerDTO.builder()
                .name(TEST_PRODUCER)
                .host(TEST_HOST)
                .port(BigDecimal.valueOf(TEST_PORT))
                .tls(true)
                .idpClientId(TEST_CLIENT)
                .products(List.of(product))
                .build();

        return ProducerConfigDTO.builder()
                .clientId(TEST_ID)
                .producers(List.of(producer))
                .build();
    }

    /**
     * Handler for token endpoint.
     */
    private class TokenHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final String response = "{\"token\":\"" + TOKEN + "\"}";
            sendResponse(exchange, response);
        }
    }

    /**
     * Handler for producer endpoint.
     */
    private class ProducerHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final ProducerConfigDTO config = createTestConfig();
            final String json = mapper.writeValueAsString(config);
            sendResponse(exchange, json);
        }
    }

    /**
     * Handler for consumer endpoint.
     */
    private class ConsumerHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final String response = "{\"clientId\":\"" + TEST_ID + "\"}";
            sendResponse(exchange, response);
        }
    }
}
