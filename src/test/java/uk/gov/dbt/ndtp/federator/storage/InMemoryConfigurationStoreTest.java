package uk.gov.dbt.ndtp.federator.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryConfigurationStore}.
 *
 * <p>This test class validates thread-safe caching operations including
 * storage, retrieval, expiration, and concurrent access of configuration data.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("In-Memory Configuration Store Tests")
class InMemoryConfigurationStoreTest {

    private static final String CLIENT_ID_1 = "client-1";
    private static final String CLIENT_ID_2 = "client-2";
    private static final String NON_EXISTENT_ID = "non-existent";
    private static final long SHORT_TTL_SECONDS = 1L;
    private static final long LONG_TTL_SECONDS = 3600L;
    private static final int THREAD_COUNT = 10;
    private static final int TIMEOUT_SECONDS = 5;

    private InMemoryConfigurationStore configStore;
    private InMemoryConfigurationStore shortTtlStore;

    /**
     * Sets up test fixtures before each test method.
     * Initializes stores with different TTL configurations.
     */
    @BeforeEach
    void setUp() {
        configStore = new InMemoryConfigurationStore(LONG_TTL_SECONDS);
        shortTtlStore = new InMemoryConfigurationStore(SHORT_TTL_SECONDS);
    }

    /**
     * Tests storage, retrieval, and cache operations for configurations.
     *
     * @throws InterruptedException if thread sleep is interrupted
     */
    @Test
    @DisplayName("Should handle cache operations correctly")
    void testCacheOperations() throws InterruptedException {
        final ProducerConfigDTO producer1 = ProducerConfigDTO.builder()
                .clientId(CLIENT_ID_1).producers(List.of()).build();
        final ConsumerConfigDTO consumer1 = ConsumerConfigDTO.builder()
                .clientId(CLIENT_ID_1).producers(List.of()).build();

        configStore.storeProducerConfig(CLIENT_ID_1, producer1);
        configStore.storeConsumerConfig(CLIENT_ID_1, consumer1);

        assertEquals(producer1, configStore.getProducerConfig(CLIENT_ID_1), "Should retrieve stored producer");
        assertEquals(consumer1, configStore.getConsumerConfig(CLIENT_ID_1), "Should retrieve stored consumer");
        assertNull(configStore.getProducerConfig(NON_EXISTENT_ID), "Should return null for non-existent");

        shortTtlStore.storeProducerConfig(CLIENT_ID_2, producer1);
        assertNotNull(shortTtlStore.getProducerConfig(CLIENT_ID_2), "Should retrieve before expiry");
        Thread.sleep(1100);
        assertNull(shortTtlStore.getProducerConfig(CLIENT_ID_2), "Should return null after expiry");

        configStore.clearCache();
        assertNull(configStore.getProducerConfig(CLIENT_ID_1), "Should return null after cache clear");
    }

    /**
     * Tests thread-safe concurrent access to the configuration store.
     *
     * @throws InterruptedException if thread execution is interrupted
     */
    @Test
    @DisplayName("Should handle concurrent access safely")
    void testConcurrentAccess() throws InterruptedException {
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT * 2);

        IntStream.range(0, THREAD_COUNT).forEach(i -> {
            executor.submit(() -> {
                try {
                    final String clientId = CLIENT_ID_1 + i;
                    final ProducerConfigDTO config = ProducerConfigDTO.builder()
                            .clientId(clientId).producers(List.of()).build();
                    configStore.storeProducerConfig(clientId, config);
                    assertEquals(config, configStore.getProducerConfig(clientId));
                } finally { latch.countDown(); }
            });
            executor.submit(() -> {
                try {
                    final String clientId = CLIENT_ID_2 + i;
                    final ConsumerConfigDTO config = ConsumerConfigDTO.builder()
                            .clientId(clientId).producers(List.of()).build();
                    configStore.storeConsumerConfig(clientId, config);
                    assertEquals(config, configStore.getConsumerConfig(clientId));
                } finally { latch.countDown(); }
            });
        });

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
    }
}