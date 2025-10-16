package uk.gov.dbt.ndtp.federator.jobs.handlers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.ConsumerConfigService;

class ClientDynamicConfigJobTest {

    private ConsumerConfigService configService;
    private JobSchedulerProvider schedulerProvider;
    private JobScheduler jobScheduler;

    @BeforeEach
    void setUp() {
        configService = mock(ConsumerConfigService.class);
        schedulerProvider = mock(JobSchedulerProvider.class);
        jobScheduler = mock(JobScheduler.class);
        when(schedulerProvider.getJobScheduler()).thenReturn(jobScheduler);
    }

    @Test
    @DisplayName("run: schedules recurring job using interval expression when type=interval")
    void run_schedulesInterval() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("interval")
                .scheduleExpression("PT2M")
                .build();
        when(configService.getConsumerConfiguration()).thenReturn(cfg);

        ClientDynamicConfigJob job = new ClientDynamicConfigJob(configService, schedulerProvider);
        job.run(JobParams.builder().managementNodeId("node-1").build());

        verify(schedulerProvider).getJobScheduler();
        verify(schedulerProvider).reloadRecurrentJobs(eq("node-1"), anyList());
    }

    @Test
    @DisplayName("run: schedules recurring job using cron expression when type=cron")
    void run_schedulesCron() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("cron")
                .scheduleExpression("0 */15 * * * *")
                .build();
        when(configService.getConsumerConfiguration()).thenReturn(cfg);

        ClientDynamicConfigJob job = new ClientDynamicConfigJob(configService, schedulerProvider);
        job.run(JobParams.builder().managementNodeId("node-2").build());

        verify(schedulerProvider).getJobScheduler();
        verify(schedulerProvider).reloadRecurrentJobs(eq("node-2"), anyList());
    }

    @Test
    @DisplayName("run: defaults to hourly interval when config invalid")
    void run_defaultsOnInvalid() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("interval")
                .scheduleExpression("NOT-A-DURATION")
                .build();
        when(configService.getConsumerConfiguration()).thenReturn(cfg);

        ClientDynamicConfigJob job = new ClientDynamicConfigJob(configService, schedulerProvider);
        job.run(JobParams.builder().managementNodeId("node-x").build());

        verify(schedulerProvider, atLeastOnce()).getJobScheduler();
        verify(schedulerProvider).reloadRecurrentJobs(eq("node-x"), anyList());
    }
}
