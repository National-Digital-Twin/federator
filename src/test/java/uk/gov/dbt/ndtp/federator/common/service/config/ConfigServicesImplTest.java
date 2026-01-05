/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

class ConfigServicesImplTest {

    private ManagementNodeDataHandler dataHandler;
    private InMemoryConfigurationStore store;

    @BeforeEach
    void setUp() {
        PropertyUtil.clear();
        dataHandler = mock(ManagementNodeDataHandler.class);
        store = mock(InMemoryConfigurationStore.class);
        PropertyUtil.init("common-configuration.properties");
    }

    @AfterEach
    void tearDown() {
        PropertyUtil.clear();
    }

    @Test
    void testProducerConfigService() {
        ProducerConfigService service = new ProducerConfigService(dataHandler, store);
        assertEquals("producer:", service.getKeyPrefix());
        assertEquals(ProducerConfigDTO.class, service.getDtoClass());
        assertEquals(store, service.getConfigStore());

        ProducerConfigDTO dto = new ProducerConfigDTO();
        when(dataHandler.getProducerData(service.getConfiguredClientId())).thenReturn(dto);
        assertEquals(dto, service.fetchConfiguration());
        assertEquals(dto, service.getProducerConfiguration());
    }

    @Test
    void testConsumerConfigService() {
        ConsumerConfigService service = new ConsumerConfigService(dataHandler, store);
        assertEquals("consumer:", service.getKeyPrefix());
        assertEquals(ConsumerConfigDTO.class, service.getDtoClass());
        assertEquals(store, service.getConfigStore());

        ConsumerConfigDTO dto = new ConsumerConfigDTO();
        when(dataHandler.getConsumerData(service.getConfiguredClientId())).thenReturn(dto);
        assertEquals(dto, service.fetchConfiguration());
        assertEquals(dto, service.getConsumerConfiguration());
    }
}
