// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.dbt.ndtp.federator.client.connection.ConfigurationException;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.grpc.GRPCClient;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.JobsConstants;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.ConsumerConfigService;

/**
 * Unit tests for FederatorClient.
 */
class FederatorClientTest {

    private static final String JOB_NAME = "DynamicConfigProvider";
    private static final int EXPECTED_RETRIES = 5;

    private FederatorClient.GRPCClientBuilder clientBuilder;
    private ConsumerConfigService configService;
    private JobSchedulerProvider scheduler;
    private FederatorClient.ExitHandler exitHandler;
    private FederatorClient federatorClient;

    /**
     * Sets up test fixtures before each test.
     */
    @BeforeEach
    void setUp() {
        clientBuilder = mock(FederatorClient.GRPCClientBuilder.class);
        configService = mock(ConsumerConfigService.class);
        // Default stub to avoid NPEs in tests; can be overridden per test
        when(configService.getConsumerConfiguration())
                .thenReturn(ConsumerConfigDTO.builder().scheduleExpression("PT5M").build());
        scheduler = mock(JobSchedulerProvider.class);
        exitHandler = mock(FederatorClient.ExitHandler.class);
        federatorClient = new FederatorClient(clientBuilder, configService, scheduler, exitHandler);
        System.setProperty("federator.test.mode", "true");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("federator.test.mode");
    }

    /**
     * Tests successful run starts scheduler and registers job.
     */
    @Test
    void testStartSchedulerAndRegisterJob() {
        federatorClient.run();

        verify(scheduler).ensureStarted();
        verify(scheduler).registerJob(any(), any());
    }

    /**
     * Tests job parameters are configured correctly.
     */
    @Test
    void testJobParametersConfiguration() {
        // scheduleExpression comes from config service (stubbed in setUp as PT5M)
        federatorClient.run();

        ArgumentCaptor<JobParams> captor = ArgumentCaptor.forClass(JobParams.class);
        verify(scheduler).registerJob(any(), captor.capture());

        JobParams params = captor.getValue();
        assertNotNull(params.getJobId());
        assertEquals(JOB_NAME, params.getJobName());
        assertEquals(EXPECTED_RETRIES, params.getAmountOfRetries());
        assertEquals("PT5M", params.getScheduleExpression());
    }

    /**
     * Verifies default schedule is used when configuration is missing.
     */
    @Test
    void testJobParametersDefaultScheduleWhenNull() {
        // Override default stub to return null schedule
        when(configService.getConsumerConfiguration())
                .thenReturn(ConsumerConfigDTO.builder().scheduleExpression(null).build());

        federatorClient.run();

        ArgumentCaptor<JobParams> captor = ArgumentCaptor.forClass(JobParams.class);
        verify(scheduler).registerJob(any(), captor.capture());

        JobParams params = captor.getValue();
        assertEquals(JobsConstants.DEFAULT_DURATION_EVERY_HOUR, params.getScheduleExpression());
    }

    /**
     * Tests configuration exception handling.
     */
    @Test
    void testHandleConfigurationException() {
        doThrow(new ConfigurationException("error")).when(scheduler).ensureStarted();

        federatorClient.run();

        verify(exitHandler).exit(1);
    }

    /**
     * Tests error handler method directly.
     */
    @Test
    void testErrorHandler() {
        federatorClient.handleError(new ConfigurationException("error"));

        verify(exitHandler).exit(1);
    }

    /**
     * Tests GRPC client builder interface.
     */
    @Test
    void testGrpcClientBuilder() {
        GRPCClient grpcClient = mock(GRPCClient.class);
        ConnectionProperties config = new ConnectionProperties("id", "NA", "server", "host", 8080, true);
        when(clientBuilder.build(config)).thenReturn(grpcClient);

        GRPCClient result = clientBuilder.build(config);

        assertNotNull(result);
        assertEquals(grpcClient, result);
    }

    /**
     * Tests constructor with default exit handler.
     */
    @Test
    void testConstructorWithDefaultExitHandler() {
        FederatorClient client = new FederatorClient(clientBuilder, configService, scheduler);

        assertNotNull(client);
    }
}
