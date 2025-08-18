package uk.gov.dbt.ndtp.federator.jobs.handlers;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.integration.model.ProductDTO;
import uk.gov.dbt.ndtp.federator.integration.services.ManagementNodeService;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.params.ClientGRPCJobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.RecurrentJobRequest;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientDynamicConfigJobTest {

    @Test
    void run_builds_requests_for_all_products_and_calls_reload_with_provided_node_id() {
        // Arrange
        ManagementNodeService management = mock(ManagementNodeService.class);
        JobSchedulerProvider scheduler = mock(JobSchedulerProvider.class);

        ConnectionProperties cp = new ConnectionProperties("client","key","ServerA","localhost",8080,false);
        when(management.getConnectionProperties("node-1")).thenReturn(List.of(cp));

        ProductDTO p1 = ProductDTO.builder().name("n1").topic("topic-1").build();
        ProductDTO p2 = ProductDTO.builder().name("n2").topic("topic-2").build();
        when(management.getProductsByProducerName("ServerA"))
                .thenReturn(Arrays.asList(p1, p2));

        ClientDynamicConfigJob job = new ClientDynamicConfigJob(management, scheduler);

        JobParams params = JobParams.builder().managementNodeId("node-1").build();

        // Act
        job.run(params);

        // Assert
        ArgumentCaptor<List<RecurrentJobRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduler, times(1)).reloadRecurrentJobs(eq("node-1"), captor.capture());

        List<RecurrentJobRequest> requests = captor.getValue();
        assertEquals(2, requests.size(), "Two job requests expected (one per topic)");

        // Validate each request contains a ClientGRPCJob with correctly filled params
        for (RecurrentJobRequest req : requests) {
            assertNotNull(req.getJob(), "Job must be set");
            assertTrue(req.getJob() instanceof ClientGRPCJob, "Job type must be ClientGRPCJob");
            assertNotNull(req.getJobParams(), "Job params must be set");
            assertTrue(req.getJobParams() instanceof ClientGRPCJobParams, "Params type must be ClientGRPCJobParams");

            ClientGRPCJobParams p = (ClientGRPCJobParams) req.getJobParams();
            assertEquals("ServerA", p.getJobName());
            assertEquals("node-1", p.getManagementNodeId());
            assertEquals(Duration.ofSeconds(30), p.getDuration());
            assertNotNull(p.getConnectionProperties());
            assertEquals(cp, p.getConnectionProperties());

            // jobId is computed as jobName+"-"+topic (see override in ClientGRPCJobParams)
            assertTrue(p.getJobId().equals("ServerA-topic-1") || p.getJobId().equals("ServerA-topic-2"),
                    "Unexpected jobId: " + p.getJobId());
        }
    }

    @Test
    void run_uses_default_node_id_when_params_are_null() {
        // Arrange
        ManagementNodeService management = mock(ManagementNodeService.class);
        JobSchedulerProvider scheduler = mock(JobSchedulerProvider.class);

        when(management.getConnectionProperties("default")).thenReturn(Collections.emptyList());

        ClientDynamicConfigJob job = new ClientDynamicConfigJob(management, scheduler);

        // Act
        job.run(null);

        // Assert
        ArgumentCaptor<List<RecurrentJobRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduler).reloadRecurrentJobs(eq("default"), captor.capture());
        assertTrue(captor.getValue().isEmpty(), "No job requests expected when no connections");
    }

    @Test
    void run_blanks_management_node_id_defaults_to_default() {
        // Arrange
        ManagementNodeService management = mock(ManagementNodeService.class);
        JobSchedulerProvider scheduler = mock(JobSchedulerProvider.class);

        when(management.getConnectionProperties("default")).thenReturn(Collections.emptyList());

        ClientDynamicConfigJob job = new ClientDynamicConfigJob(management, scheduler);
        JobParams params = JobParams.builder().managementNodeId("  ").build();

        // Act
        job.run(params);

        // Assert
        verify(scheduler).reloadRecurrentJobs(eq("default"), anyList());
    }
}
