/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;

class ConfigServiceTest {

    private InMemoryConfigurationStore store;
    private ConfigService<String> service;
    private int fetchCount = 0;

    @BeforeEach
    void setUp() {
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
