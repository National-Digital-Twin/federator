// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.jobs.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.params.ClientGRPCJobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.RecurrentJobRequest;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataException;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.service.FederatorConfigurationService;

/**
 * Unit tests for {@link ClientDynamicConfigJob}.
 *
 * @author National Digital Twin Programme
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClientDynamicConfigJob")
class ClientDynamicConfigJobTest {

    private static final String TEST_NODE = "test-node";
    private static final String DEFAULT_NODE = "default";
    private static final String TEST_PRODUCER = "TestProducer";
    private static final String TEST_HOST = "test.host.com";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_TOPIC = "test-topic";
    private static final String TEST_PRODUCT = "TestProduct";
    private static final String ERROR_MSG = "Error";
    private static final int TEST_PORT = 8080;

    @Mock
    private FederatorConfigurationService configService;

    @Mock
    private JobSchedulerProvider scheduler;

    private ClientDynamicConfigJob job;

    /**
     * Sets up test fixtures before each test.
     */
    @BeforeEach
    void setUp() {
        job = new ClientDynamicConfigJob(configService, scheduler);
    }

    /**
     * Tests for parameter handling.
     */
    @Nested
    @DisplayName("Parameter Handling")
    class ParameterHandling {

        /**
         * Tests null parameter handling.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should use default node for null params")
        void shouldUseDefaultNodeForNullParams()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenReturn(createEmptyConfig());

            job.run(null);

            verify(scheduler).reloadRecurrentJobs(
                    eq(DEFAULT_NODE), any());
        }

        /**
         * Tests blank node ID handling.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should use default for blank node ID")
        void shouldUseDefaultForBlankNodeId()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenReturn(createEmptyConfig());

            job.run(JobParams.builder()
                    .managementNodeId("").build());

            verify(scheduler).reloadRecurrentJobs(
                    eq(DEFAULT_NODE), any());
        }

        /**
         * Tests valid node ID handling.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should use provided node ID")
        void shouldUseProvidedNodeId()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenReturn(createEmptyConfig());

            job.run(createParams());

            verify(scheduler).reloadRecurrentJobs(
                    eq(TEST_NODE), any());
        }
    }

    /**
     * Tests for configuration processing.
     */
    @Nested
    @DisplayName("Configuration Processing")
    class ConfigurationProcessing {

        /**
         * Tests valid configuration processing.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should process valid configuration")
        void shouldProcessValidConfiguration()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenReturn(createValidConfig());

            job.run(createParams());

            final ArgumentCaptor<List<RecurrentJobRequest>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(scheduler).reloadRecurrentJobs(
                    eq(TEST_NODE), captor.capture());

            final List<RecurrentJobRequest> requests =
                    captor.getValue();
            assertNotNull(requests);
            assertEquals(1, requests.size());

            final ClientGRPCJobParams params =
                    (ClientGRPCJobParams) requests.get(0).getJobParams();
            assertEquals(TEST_TOPIC, params.getTopic());
        }

        /**
         * Tests empty producer list handling.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should handle empty producers")
        void shouldHandleEmptyProducers()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenReturn(createEmptyConfig());

            job.run(createParams());

            verify(scheduler).reloadRecurrentJobs(
                    eq(TEST_NODE), eq(Collections.emptyList()));
        }

        /**
         * Tests null configuration handling.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should handle null configuration")
        void shouldHandleNullConfiguration()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenReturn(null);

            job.run(createParams());

            verify(scheduler, never())
                    .reloadRecurrentJobs(anyString(), any());
        }

        /**
         * Tests null producer handling.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should skip null producers")
        void shouldSkipNullProducers()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenReturn(createConfigWithNullProducer());

            job.run(createParams());

            final ArgumentCaptor<List<RecurrentJobRequest>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(scheduler).reloadRecurrentJobs(
                    eq(TEST_NODE), captor.capture());

            final List<RecurrentJobRequest> requests =
                    captor.getValue();
            assertNotNull(requests);
            assertEquals(1, requests.size());
        }

        /**
         * Tests default values usage.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should use default port and TLS")
        void shouldUseDefaultPortAndTls()
                throws ManagementNodeDataException {
            final ProducerConfigDTO config = createMinimalConfig();
            when(configService.getProducerConfiguration())
                    .thenReturn(config);

            job.run(createParams());

            final ArgumentCaptor<List<RecurrentJobRequest>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(scheduler).reloadRecurrentJobs(
                    eq(TEST_NODE), captor.capture());

            final List<RecurrentJobRequest> requests =
                    captor.getValue();
            assertNotNull(requests);
            assertEquals(1, requests.size());

            final ClientGRPCJobParams params =
                    (ClientGRPCJobParams) requests.get(0).getJobParams();
            assertNotNull(params.getConnectionProperties());
        }
    }

    /**
     * Tests for error handling.
     */
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        /**
         * Tests exception handling.
         *
         * @throws ManagementNodeDataException on error
         */
        @Test
        @DisplayName("should handle fetch exception")
        void shouldHandleFetchException()
                throws ManagementNodeDataException {
            when(configService.getProducerConfiguration())
                    .thenThrow(new ManagementNodeDataException(ERROR_MSG));

            job.run(createParams());

            verify(scheduler, never())
                    .reloadRecurrentJobs(anyString(), any());
        }
    }

    /**
     * Creates test job parameters.
     *
     * @return job parameters
     */
    private JobParams createParams() {
        return JobParams.builder()
                .managementNodeId(TEST_NODE)
                .build();
    }

    /**
     * Creates empty configuration.
     *
     * @return empty config
     */
    private ProducerConfigDTO createEmptyConfig() {
        return ProducerConfigDTO.builder()
                .producers(Collections.emptyList())
                .build();
    }

    /**
     * Creates valid configuration.
     *
     * @return valid config
     */
    private ProducerConfigDTO createValidConfig() {
        final ProductDTO product = ProductDTO.builder()
                .name(TEST_PRODUCT)
                .topic(TEST_TOPIC)
                .build();

        final ProducerDTO producer = ProducerDTO.builder()
                .name(TEST_PRODUCER)
                .host(TEST_HOST)
                .port(BigDecimal.valueOf(TEST_PORT))
                .tls(true)
                .idpClientId(TEST_CLIENT_ID)
                .dataProviders(Arrays.asList(product))
                .build();

        return ProducerConfigDTO.builder()
                .producers(Arrays.asList(producer))
                .build();
    }

    /**
     * Creates configuration with null producer.
     *
     * @return config with null
     */
    private ProducerConfigDTO createConfigWithNullProducer() {
        final ProductDTO product = ProductDTO.builder()
                .topic(TEST_TOPIC)
                .build();

        final ProducerDTO producer = ProducerDTO.builder()
                .name(TEST_PRODUCER)
                .host(TEST_HOST)
                .idpClientId(TEST_CLIENT_ID)
                .dataProviders(Arrays.asList(product))
                .build();

        return ProducerConfigDTO.builder()
                .producers(Arrays.asList(null, producer))
                .build();
    }

    /**
     * Creates minimal configuration.
     *
     * @return minimal config
     */
    private ProducerConfigDTO createMinimalConfig() {
        final ProductDTO product = ProductDTO.builder()
                .topic(TEST_TOPIC)
                .build();

        final ProducerDTO producer = ProducerDTO.builder()
                .name(TEST_PRODUCER)
                .host(TEST_HOST)
                .idpClientId(TEST_CLIENT_ID)
                .dataProviders(Arrays.asList(product))
                .build();

        return ProducerConfigDTO.builder()
                .producers(Arrays.asList(producer))
                .build();
    }
}