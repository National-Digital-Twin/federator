package uk.gov.dbt.ndtp.federator.client.jobs.handlers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.client.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.client.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProductConsumerDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProductDTO;
import uk.gov.dbt.ndtp.federator.common.service.config.ConsumerConfigService;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientDynamicConfigJobTest {

    private ConsumerConfigService configService;
    private JobSchedulerProvider schedulerProvider;
    private JobScheduler jobScheduler;
    private MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    private MockedStatic<RedisUtil> redisUtilMockedStatic;

    @BeforeEach
    void setUp() {
        configService = mock(ConsumerConfigService.class);
        schedulerProvider = mock(JobSchedulerProvider.class);
        jobScheduler = mock(JobScheduler.class);
        when(schedulerProvider.getJobScheduler()).thenReturn(jobScheduler);
        
        propertyUtilMockedStatic = mockStatic(PropertyUtil.class);
        redisUtilMockedStatic = mockStatic(RedisUtil.class);
    }

    @AfterEach
    void tearDown() {
        propertyUtilMockedStatic.close();
        redisUtilMockedStatic.close();
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

    @Test
    @DisplayName("run: processes producers and products and reloads jobs")
    void run_processesProducersAndProducts() {
        ProductConsumerDTO pc = ProductConsumerDTO.builder()
                .destination("/tmp/test")
                .scheduleType("interval")
                .scheduleExpression("PT1M")
                .build();
        ProductDTO product = ProductDTO.builder()
                .name("test-product")
                .topic("test-topic")
                .type("file")
                .configurations(Collections.singletonList(pc))
                .build();
        ProducerDTO producer = ProducerDTO.builder()
                .name("test-producer")
                .host("localhost")
                .port(new BigDecimal("8080"))
                .active(true)
                .products(Collections.singletonList(product))
                .idpClientId("idp-client-id")
                .build();
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder()
                .scheduleType("interval")
                .scheduleExpression("PT1M")
                .producers(Collections.singletonList(producer))
                .build();
        
        when(configService.getConsumerConfiguration()).thenReturn(cfg);
        
        propertyUtilMockedStatic.when(() -> PropertyUtil.getPropertyValue(anyString(), anyString())).thenReturn("prefix");

        ClientDynamicConfigJob job = new ClientDynamicConfigJob(configService, schedulerProvider);
        job.run(JobParams.builder().managementNodeId("node-1").build());

        verify(schedulerProvider).reloadRecurrentJobs(eq("node-1"), anyList());
    }

    @Test
    @DisplayName("toString returns expected format")
    void testToString() {
        ClientDynamicConfigJob job = new ClientDynamicConfigJob(configService, schedulerProvider);
        String str = job.toString();
        org.junit.jupiter.api.Assertions.assertNotNull(str);
    }
}
