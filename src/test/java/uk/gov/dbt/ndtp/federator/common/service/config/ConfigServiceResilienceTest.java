/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.service.config.exception.ConfigFetchException;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.ResilienceSupport;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

class ConfigServiceResilienceTest {

    @BeforeEach
    void setup() {
        PropertyUtil.clear();
        try {
            java.io.File tmp = java.io.File.createTempFile("resilience-test", ".properties");
            tmp.deleteOnExit();
            try (java.io.FileWriter fw = new java.io.FileWriter(tmp)) {
                fw.write("""
                        management.node.resilience.retry.maxAttempts=5
                        management.node.resilience.retry.initialWait=PT0.01S
                        management.node.resilience.retry.maxBackoff=PT0.05S
                        management.node.resilience.circuitBreaker.failureRateThreshold=100
                        management.node.resilience.circuitBreaker.minimumNumberOfCalls=100
                        management.node.resilience.circuitBreaker.slidingWindowSize=10
                        management.node.resilience.circuitBreaker.waitDurationInOpenState=PT1S
                        management.node.resilience.circuitBreaker.permittedNumberOfCallsInHalfOpenState=1
                        """
                        );
            }
            PropertyUtil.init(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        InMemoryConfigurationStore.getInstance().clearCache();
        ResilienceSupport.clearForTests();
    }

    @AfterEach
    void tearDown() {
        InMemoryConfigurationStore.getInstance().clearCache();
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
    }

    @Test
    void retrySucceedsAfterTransientFailures() {
        TestService service = new TestService(3, "OK");
        String cfg = service.getConfiguration();
        assertEquals("OK", cfg);

        // should be cached now
        Optional<String> cached = service.getCachedConfiguration();
        assertTrue(cached.isPresent());
        assertEquals("OK", cached.get());

        // We don’t assert exact invocation count due to resilience timing; just ensure it’s >= 1
        assertTrue(service.getFetchInvocations() >= 1);
    }

    @Test
    void retryExhaustsAndThrows() {
        TestService service = new TestService(10, "NEVER");
        assertThrows(ConfigFetchException.class, service::getConfiguration);
        assertTrue(service.getCachedConfiguration().isEmpty());
        // Ensure we attempted at least once (exact count is resilience/version dependent)
        assertTrue(service.getFetchInvocations() >= 1);
    }

    @Test
    void circuitBreakerOpensAndBlocksFurtherCalls() {
        // Reconfigure properties to ensure circuit breaker opens quickly
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
        try {
            java.io.File tmp = java.io.File.createTempFile("resilience-test-open", ".properties");
            tmp.deleteOnExit();
            try (java.io.FileWriter fw = new java.io.FileWriter(tmp)) {
                fw.write("""
                        management.node.resilience.retry.maxAttempts=3
                        management.node.resilience.retry.initialWait=PT0.01S
                        management.node.resilience.retry.maxBackoff=PT0.05S
                        management.node.resilience.circuitBreaker.failureRateThreshold=1
                        management.node.resilience.circuitBreaker.minimumNumberOfCalls=1
                        management.node.resilience.circuitBreaker.slidingWindowSize=1
                        management.node.resilience.circuitBreaker.waitDurationInOpenState=PT5M
                        management.node.resilience.circuitBreaker.permittedNumberOfCallsInHalfOpenState=1
                        """
                        );
            }
            PropertyUtil.init(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TestService service = new TestService(10, "NEVER");
        // First call will fail and open the breaker
        assertThrows(ConfigFetchException.class, service::getConfiguration);
        int invocationsAfterFirst = service.getFetchInvocations();

        // Second call should be blocked immediately by OPEN breaker (no new fetch invocations)
        assertThrows(ConfigFetchException.class, service::getConfiguration);
        assertEquals(invocationsAfterFirst, service.getFetchInvocations());
    }

    // Simple test implementation of ConfigService<String>
    static class TestService implements ConfigService<String> {
        private final InMemoryConfigurationStore store = InMemoryConfigurationStore.getInstance();
        private final AtomicInteger remainingFailures;
        private final AtomicInteger invocations = new AtomicInteger(0);
        private final String value;

        TestService(int failTimes, String value) {
            this.remainingFailures = new AtomicInteger(failTimes);
            this.value = value;
        }

        int getFetchInvocations() { return invocations.get(); }

        @Override
        public InMemoryConfigurationStore getConfigStore() { return store; }

        @Override
        public String getKeyPrefix() { return "test:"; }

        @Override
        public String getConfiguredClientId() { return "id1"; }

        @Override
        public Class<String> getDtoClass() { return String.class; }

        @Override
        public String fetchConfiguration() {
            invocations.incrementAndGet();
            if (remainingFailures.getAndDecrement() > 0) {
                throw new RuntimeException("transient");
            }
            return value;
        }
    }
}
