// SPDX-License-Identifier: Apache-2.0
package uk.gov.dbt.ndtp.federator.common.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.service.config.ProducerConfigService;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.service.config.exception.ConfigFetchException;

class ProducerConfigServiceTest {

    private ManagementNodeDataHandler dataHandler;
    private InMemoryConfigurationStore configStore;
    private ProducerConfigService service;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure PropertyUtil is initialized so service constructors can call PropertyUtil safely
        PropertyUtil.clear();
        File tmp = File.createTempFile("test-prop", ".properties");
        tmp.deleteOnExit();
        try (FileWriter fw = new FileWriter(tmp)) {
            // write nothing; services call getPropertyValue(key, default) so defaults are used
            fw.write("");
        }
        PropertyUtil.init(tmp);

        dataHandler = mock(ManagementNodeDataHandler.class);
        configStore = mock(InMemoryConfigurationStore.class);
        service = new ProducerConfigService(dataHandler, configStore);
    }

    @Test
    void getProducerConfiguration_returnsCached_whenPresent() {
        ProducerConfigDTO cfg = ProducerConfigDTO.builder().clientId("c1").build();
        when(configStore.get(anyString(), eq(ProducerConfigDTO.class))).thenReturn(Optional.of(cfg));

        ProducerConfigDTO result = service.getProducerConfiguration();

        assertNotNull(result);
        assertEquals("c1", result.getClientId());
        verify(configStore, times(1)).get(anyString(), eq(ProducerConfigDTO.class));
        verifyNoInteractions(dataHandler);
    }

    @Test
    void getProducerConfiguration_fetchesAndStores_whenCacheMiss() {
        ProducerConfigDTO cfg = ProducerConfigDTO.builder().clientId("c2").build();
        // first call to get -> empty, second call -> present
        when(configStore.get(anyString(), eq(ProducerConfigDTO.class))).thenReturn(Optional.empty(), Optional.of(cfg));
        when(dataHandler.getProducerData(any())).thenReturn(cfg);

        ProducerConfigDTO result = service.getProducerConfiguration();

        assertNotNull(result);
        assertEquals("c2", result.getClientId());
        verify(dataHandler, times(1)).getProducerData(any());
        verify(configStore, atLeastOnce()).store(anyString(), eq(cfg));
    }

    @Test
    void clearCache_delegatesToStore() {
        service.clearCache();
        verify(configStore, times(1)).clearCache();
    }

    @Test
    void refreshConfigurations_clearsAndPopulates() {
        ProducerConfigDTO cfg = ProducerConfigDTO.builder().clientId("c3").build();
        when(dataHandler.getProducerData(any())).thenReturn(cfg);
        // get will be called by populateCache; simulate empty then filled
        when(configStore.get(anyString(), eq(ProducerConfigDTO.class))).thenReturn(Optional.empty(), Optional.of(cfg));

        service.refreshConfigurations();

        verify(configStore, times(1)).clearCache();
        verify(dataHandler, times(1)).getProducerData(any());
        verify(configStore, atLeastOnce()).store(anyString(), eq(cfg));
    }

    @Test
    void getProducerConfiguration_propagatesConfigFetchException_fromDataHandler() {
        when(configStore.get(anyString(), eq(ProducerConfigDTO.class))).thenReturn(Optional.empty());
        when(dataHandler.getProducerData(any())).thenThrow(new RuntimeException("fail"));

        assertThrows(ConfigFetchException.class, () -> service.getProducerConfiguration());
        verify(configStore).get(anyString(), eq(ProducerConfigDTO.class));
        verify(dataHandler, atLeast(1)).getProducerData(any());
    }
}
