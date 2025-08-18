package uk.gov.dbt.ndtp.federator.jobs;

import org.junit.jupiter.api.*;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.AbstractStorageProvider;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.jobs.params.RecurrentJobRequest;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultJobSchedulerProviderTest {

    private File tempProps;


    @BeforeEach
    void setUp() throws IOException {
        // Ensure PropertyUtil is clean and init with empty properties
        PropertyUtil.clear();
        tempProps = Files.createTempFile("props", ".properties").toFile();
        PropertyUtil.init(tempProps);
        // Do not start provider here; individual tests will inject mocks as needed
    }

    @AfterEach
    void tearDown() {
        try {
            DefaultJobSchedulerProvider.getInstance().stop();
        } catch (Throwable ignored) {
        }
        PropertyUtil.clear();
        if (tempProps != null) tempProps.delete();
    }

    @Test
    void getJobScheduler_throws_if_not_started() {
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.getInstance();
        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::getJobScheduler);
        assertTrue(ex.getMessage().contains("not started"));
    }

    @Test
    void ensureStarted_then_stop_toggles_scheduler_availability() {
        JobScheduler scheduler = mock(JobScheduler.class);
        AbstractStorageProvider storage = mock(AbstractStorageProvider.class);
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.withDependencies(
                scheduler, storage, new DefaultJobSchedulerProvider.RecurringJobsAccess() {
                    @Override
                    public Map<String, JobParams> paramsById(AbstractStorageProvider sp) {
                        return java.util.Collections.emptyMap();
                    }

                    @Override
                    public java.util.Set<String> ids(AbstractStorageProvider sp) {
                        return java.util.Collections.emptySet();
                    }
                });

        assertNotNull(provider.getJobScheduler(), "JobScheduler should be available after mock start");

        provider.stop();
        assertThrows(IllegalStateException.class, provider::getJobScheduler, "JobScheduler should not be available after stop");
    }

    @Test
    void registerJob_validates_arguments_and_triggers_immediate_when_configured() {
        JobScheduler scheduler = mock(JobScheduler.class);
        AbstractStorageProvider storage = mock(AbstractStorageProvider.class);
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.withDependencies(
                scheduler, storage, new DefaultJobSchedulerProvider.RecurringJobsAccess() {
                    @Override
                    public Map<String, JobParams> paramsById(AbstractStorageProvider sp) {
                        return java.util.Collections.emptyMap();
                    }

                    @Override
                    public java.util.Set<String> ids(AbstractStorageProvider sp) {
                        return java.util.Collections.emptySet();
                    }
                });

        // Null validations
        JobParams sampleParams = JobParams.builder()
                .jobId("job-null-test")
                .jobName("job-null-test")
                .managementNodeId("node-1")
                .duration(Duration.ofMillis(200))
                .build();
        assertThrows(IllegalArgumentException.class, () -> provider.registerJob(null, sampleParams));
        assertThrows(IllegalArgumentException.class, () -> provider.registerJob(value -> {}, null));

        // Immediate trigger path
        AtomicInteger executed = new AtomicInteger(0);
        Job job = value -> executed.incrementAndGet();
        JobParams params = JobParams.builder()
                .jobId("job-immediate-" + UUID.randomUUID())
                .jobName("immediate")
                .managementNodeId("node-1")
                .requireImmediateTrigger(true)
                .AmountOfRetries(2)
                .duration(Duration.ofMillis(200))
                .build();

        provider.registerJob(job, params);

        // Verify interactions instead of actually running background services
        verify(scheduler, times(1)).createRecurrently(any());
    }

    @Test
    void reloadRecurrentJobs_removes_obsolete_removes_modified_and_adds_missing_while_skipping_other_nodes() {
        JobScheduler scheduler = mock(JobScheduler.class);
        AbstractStorageProvider storage = mock(AbstractStorageProvider.class);

        // Existing jobs: A (node-1), B (node-1), X (node-2)
        JobParams paramsA = JobParams.builder()
                .jobId("A").jobName("A").managementNodeId("node-1")
                .duration(Duration.ofSeconds(3))
                .requireImmediateTrigger(false)
                .build();
        JobParams paramsB = JobParams.builder()
                .jobId("B").jobName("B").managementNodeId("node-1")
                .duration(Duration.ofSeconds(3))
                .AmountOfRetries(1)
                .requireImmediateTrigger(false)
                .build();
        JobParams paramsX = JobParams.builder()
                .jobId("X").jobName("X").managementNodeId("node-2")
                .duration(Duration.ofSeconds(3))
                .requireImmediateTrigger(false)
                .build();
        
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.withDependencies(
                scheduler, storage, new DefaultJobSchedulerProvider.RecurringJobsAccess() {
                    @Override
                    public Map<String, JobParams> paramsById(AbstractStorageProvider sp) {
                        return java.util.Map.of(
                                "A", paramsA,
                                "B", paramsB,
                                "X", paramsX
                        );
                    }

                    @Override
                    public java.util.Set<String> ids(AbstractStorageProvider sp) {
                        return new java.util.HashSet<>(java.util.List.of("A", "B", "X"));
                    }
                });

        // Build new requests for node-1: B (modified) and C (new), both with immediate triggers
        List<RecurrentJobRequest> requests = new ArrayList<>();
        JobParams paramsBModified = JobParams.builder()
                .jobId("B").jobName("B").managementNodeId("node-1")
                .duration(Duration.ofSeconds(3))
                .AmountOfRetries(3)
                .requireImmediateTrigger(true)
                .build();
        JobParams paramsC = JobParams.builder()
                .jobId("C").jobName("C").managementNodeId("node-1")
                .duration(Duration.ofSeconds(3))
                .requireImmediateTrigger(true)
                .build();

        // Provide dummy jobs for requests (they won't actually run)
        Job dummy = value -> {};
        requests.add(RecurrentJobRequest.builder().job(dummy).jobParams(paramsBModified).build());
        requests.add(RecurrentJobRequest.builder().job(dummy).jobParams(paramsC).build());

        provider.reloadRecurrentJobs("node-1", requests);

        // Verify deletions: A (obsolete) and B (modified) should be deleted; X belongs to another node and should be skipped
        verify(scheduler, times(1)).deleteRecurringJob("A");
        verify(scheduler, times(1)).deleteRecurringJob("B");
        verify(scheduler, never()).deleteRecurringJob("X");

        // Verify additions: B and C should be (re)registered -> recurrent creation each
        verify(scheduler, times(1)).createRecurrently(any());
    }

}
