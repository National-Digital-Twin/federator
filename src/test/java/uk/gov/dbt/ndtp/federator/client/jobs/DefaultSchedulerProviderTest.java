// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.client.jobs;

import static org.junit.jupiter.api.Assertions.*;

import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.*;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

class DefaultSchedulerProviderTest {

    @BeforeAll
    static void setupProperties() {
        // Ensure PropertyUtil is initialised for tests
        PropertyUtil.clear();
        PropertyUtil.init("test-jobrunr.properties");
    }

    @AfterEach
    void tearDown() {
        // Ensure scheduler is stopped between tests to avoid bleed-over
        DefaultJobSchedulerProvider.getInstance().stop();
    }

    @Test
    void getJobScheduler_throwsWhenNotStarted() {
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.getInstance();
        provider.stop();

        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::getJobScheduler);
        assertEquals("JobScheduler not started", ex.getMessage());
    }

    @Test
    void getJobScheduler_returnsAfterEnsureStarted() {
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.getInstance();
        provider.ensureStarted();

        JobScheduler scheduler = provider.getJobScheduler();
        assertNotNull(scheduler, "JobScheduler should be available after ensureStarted()");
    }

    @Test
    void ensureStarted_isIdempotent_andReturnsSameInstance() {
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.getInstance();
        provider.ensureStarted();
        JobScheduler first = provider.getJobScheduler();

        // Call ensureStarted again - should not reinitialise or throw
        provider.ensureStarted();
        JobScheduler second = provider.getJobScheduler();

        assertSame(first, second, "JobScheduler instance should be stable across repeated ensureStarted() calls");
    }

    @Test
    void getJobScheduler_throwsAfterStop() {
        DefaultJobSchedulerProvider provider = DefaultJobSchedulerProvider.getInstance();
        provider.ensureStarted();
        assertNotNull(provider.getJobScheduler());

        provider.stop();
        assertThrows(IllegalStateException.class, provider::getJobScheduler);
    }
}
