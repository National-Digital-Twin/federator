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
import uk.gov.dbt.ndtp.federator.common.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.service.config.ConsumerConfigService;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.service.config.exception.ConfigFetchException;
import uk.gov.dbt.ndtp.federator.common.utils.ResilienceSupport;

class ConsumerConfigServiceTest {

    private ManagementNodeDataHandler dataHandler;
    private InMemoryConfigurationStore configStore;
    private ConsumerConfigService service;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure PropertyUtil is initialized so service constructors can call PropertyUtil safely
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
        File tmp = File.createTempFile("test-prop", ".properties");
        tmp.deleteOnExit();
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write("""
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

        dataHandler = mock(ManagementNodeDataHandler.class);
        configStore = mock(InMemoryConfigurationStore.class);
        service = new ConsumerConfigService(dataHandler, configStore);
    }

    @Test
    void getConsumerConfiguration_returnsCached_whenPresent() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder().clientId("c1").build();
        when(configStore.get(anyString(), eq(ConsumerConfigDTO.class))).thenReturn(Optional.of(cfg));

        ConsumerConfigDTO result = service.getConsumerConfiguration();

        assertNotNull(result);
        assertEquals("c1", result.getClientId());
        verify(configStore, times(1)).get(anyString(), eq(ConsumerConfigDTO.class));
        verifyNoInteractions(dataHandler);
    }

    @Test
    void getConsumerConfiguration_fetchesAndStores_whenCacheMiss() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder().clientId("c2").build();
        when(configStore.get(anyString(), eq(ConsumerConfigDTO.class))).thenReturn(Optional.empty(), Optional.of(cfg));
        when(dataHandler.getConsumerData(any())).thenReturn(cfg);

        ConsumerConfigDTO result = service.getConsumerConfiguration();

        assertNotNull(result);
        assertEquals("c2", result.getClientId());
        verify(dataHandler, times(1)).getConsumerData(any());
        verify(configStore, atLeastOnce()).store(anyString(), eq(cfg));
    }

    @Test
    void clearCache_delegatesToStore() {
        service.clearCache();
        verify(configStore, times(1)).clearCache();
    }

    @Test
    void refreshConfigurations_clearsAndPopulates() {
        ConsumerConfigDTO cfg = ConsumerConfigDTO.builder().clientId("c3").build();
        when(dataHandler.getConsumerData(any())).thenReturn(cfg);
        when(configStore.get(anyString(), eq(ConsumerConfigDTO.class))).thenReturn(Optional.empty(), Optional.of(cfg));

        service.refreshConfigurations();

        verify(configStore, times(1)).clearCache();
        verify(dataHandler, times(1)).getConsumerData(any());
        verify(configStore, atLeastOnce()).store(anyString(), eq(cfg));
    }

    @Test
    void getConsumerConfiguration_propagatesConfigFetchException_fromDataHandler() {
        when(configStore.get(anyString(), eq(ConsumerConfigDTO.class))).thenReturn(Optional.empty());
        when(dataHandler.getConsumerData(any())).thenThrow(new RuntimeException("fail"));

        assertThrows(ConfigFetchException.class, () -> service.getConsumerConfiguration());
        verify(configStore).get(anyString(), eq(ConsumerConfigDTO.class));
        verify(dataHandler, atLeast(1)).getConsumerData(any());
    }
}
