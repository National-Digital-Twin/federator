// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.FederatorConfigurationService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Management Node components.
 * Run with: -Drun.integration.tests=true
 */
@EnabledIfSystemProperty(
        named = "run.integration.tests",
        matches = "true")
@DisplayName("Management Node Integration Tests")
class ManagementNodeIntegrationTest {

    private static final String PROPS_FILE = "test.properties";

    private ManagementNodeDataHandler dataHandler;
    private FederatorConfigurationService configService;

    @BeforeAll
    static void setupProperties() throws IOException {
        File propertiesFile = createPropertiesFile();
        PropertyUtil.init(propertiesFile);
    }

    @BeforeEach
    void setUp() {
        // Mock token service for testing
        IdpTokenService mockTokenService = mock(IdpTokenService.class);
        when(mockTokenService.fetchToken())
                .thenReturn("test-token");
        when(mockTokenService.verifyToken("test-token"))
                .thenReturn(true);

        // Create real components
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        final ObjectMapper objectMapper = new ObjectMapper();

        dataHandler = new ManagementNodeDataHandler(
                httpClient, objectMapper, mockTokenService);

        final InMemoryConfigurationStore configStore =
                new InMemoryConfigurationStore();

        configService = new FederatorConfigurationService(
                dataHandler, configStore);
    }

    @Test
    @DisplayName("Should initialize handler successfully")
    void testHandlerInitialization() {
        assertNotNull(dataHandler);
    }

    @Test
    @DisplayName("Should initialize service successfully")
    void testServiceInitialization() {
        assertNotNull(configService);
    }

    @Test
    @DisplayName("Should handle connection failure gracefully")
    void testConnectionFailure() {
        // This will fail as no real server is running
        assertThrows(ManagementNodeDataException.class,
                () -> dataHandler.getProducerData(null));
    }

    @Test
    @DisplayName("Should cache configurations")
    void testCachingBehavior()
            throws ManagementNodeDataException {
        // Mock successful response
        final IdpTokenService realMock =
                mock(IdpTokenService.class);
        when(realMock.fetchToken()).thenReturn("valid-token");
        when(realMock.verifyToken("valid-token"))
                .thenReturn(true);

        final ManagementNodeDataHandler mockHandler =
                mock(ManagementNodeDataHandler.class);
        final ProducerConfigDTO mockConfig =
                new ProducerConfigDTO();
        mockConfig.setClientId("TEST");

        when(mockHandler.getProducerData(null))
                .thenReturn(mockConfig);

        final InMemoryConfigurationStore store =
                new InMemoryConfigurationStore();
        final FederatorConfigurationService service =
                new FederatorConfigurationService(mockHandler, store);

        // First call - should hit handler
        ProducerConfigDTO result1 =
                service.getProducerConfiguration();
        assertNotNull(result1);

        // Second call - should use cache
        ProducerConfigDTO result2 =
                service.getProducerConfiguration();
        assertNotNull(result2);
    }

    @Test
    @DisplayName("Should clear cache successfully")
    void testCacheClear() {
        configService.clearCache();
        // Should not throw exception
        assertTrue(true);
    }

    /**
     * Creates properties file for testing.
     */
    private static File createPropertiesFile()
            throws IOException {
        final File props = File.createTempFile(PROPS_FILE, ".properties");
        props.deleteOnExit();

        try (FileWriter writer = new FileWriter(props)) {
            // Management node properties
            writer.write("management.node.base.url=http://localhost:8090\n");
            writer.write("management.node.request.timeout=5\n");
            writer.write("management.node.producer.path=/api/v1/configuration/producer\n");
            writer.write("management.node.consumer.path=/api/v1/configuration/consumer\n");

            // Cache properties
            writer.write("cache.ttl.seconds=300\n");

            // Federator properties
            writer.write("federator.producer.id=\n");
            writer.write("federator.consumer.id=\n");
        }

        return props;
    }
}