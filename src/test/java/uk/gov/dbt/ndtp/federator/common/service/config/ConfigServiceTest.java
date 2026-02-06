/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.ResilienceSupport;

class ConfigServiceTest {

    private InMemoryConfigurationStore store;
    private ConfigService<String> service;
    private int fetchCount = 0;

    @BeforeEach
    void setUp() {
        PropertyUtil.clear();
        try {
            java.io.File tmp = java.io.File.createTempFile("resilience-test", ".properties");
            tmp.deleteOnExit();
            try (java.io.FileWriter fw = new java.io.FileWriter(tmp)) {
                fw.write(
                        """
                        management.node.resilience.retry.maxAttempts=5
                        management.node.resilience.retry.initialWait=PT0.01S
                        management.node.resilience.retry.maxBackoff=PT0.05S
                        management.node.resilience.circuitBreaker.failureRateThreshold=100
                        management.node.resilience.circuitBreaker.minimumNumberOfCalls=100
                        management.node.resilience.circuitBreaker.slidingWindowSize=10
                        management.node.resilience.circuitBreaker.waitDurationInOpenState=PT1S
                        management.node.resilience.circuitBreaker.permittedNumberOfCallsInHalfOpenState=1
                        """);
            }
            PropertyUtil.init(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        InMemoryConfigurationStore.getInstance().clearCache();
        ResilienceSupport.clearForTests();

        store = mock(InMemoryConfigurationStore.class);
        fetchCount = 0;
        service = new ConfigService<String>() {
            @Override
            public InMemoryConfigurationStore getConfigStore() {
                return store;
            }

            @Override
            public String getKeyPrefix() {
                return "test:";
            }

            @Override
            public String getConfiguredClientId() {
                return "client1";
            }

            @Override
            public Class<String> getDtoClass() {
                return String.class;
            }

            @Override
            public String fetchConfiguration() {
                fetchCount++;
                return "configValue";
            }
        };
    }

    @AfterEach
    void tearDown() {
        InMemoryConfigurationStore.getInstance().clearCache();
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
    }

    @Test
    void testBuildCacheKey() {
        assertEquals("test:client1", service.buildCacheKey());

        ConfigService<String> noClientService = new ConfigService<String>() {
            @Override
            public InMemoryConfigurationStore getConfigStore() {
                return store;
            }

            @Override
            public String getKeyPrefix() {
                return "test:";
            }

            @Override
            public String getConfiguredClientId() {
                return null;
            }

            @Override
            public Class<String> getDtoClass() {
                return String.class;
            }

            @Override
            public String fetchConfiguration() {
                return null;
            }
        };
        assertEquals("test:default", noClientService.buildCacheKey());
    }

    @Test
    void testGetConfiguration_cached() {
        when(store.get("test:client1", String.class)).thenReturn(Optional.of("cachedValue"));

        String result = service.getConfiguration();

        assertEquals("cachedValue", result);
        assertEquals(0, fetchCount);
    }

    @Test
    void testGetConfiguration_notCached() {
        when(store.get("test:client1", String.class)).thenReturn(Optional.empty());

        String result = service.getConfiguration();

        assertEquals("configValue", result);
        assertEquals(1, fetchCount);
        verify(store).store("test:client1", "configValue");
    }

    @Test
    void testRefreshConfigurations() {
        service.refreshConfigurations();

        verify(store).clearCache();
        verify(store).store("test:client1", "configValue");
        assertEquals(1, fetchCount);
    }

    @Test
    void testClearCache() {
        service.clearCache();
        verify(store).clearCache();
    }
}
