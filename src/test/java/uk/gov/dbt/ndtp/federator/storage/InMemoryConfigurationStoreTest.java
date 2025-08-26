// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.utils.CommonPropertiesLoader;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryConfigurationStore.
 */
class InMemoryConfigurationStoreTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private InMemoryConfigurationStore store;

    @BeforeEach
    void setUp() {
        CommonPropertiesLoader.loadTestProperties();
        store = new InMemoryConfigurationStore();
    }

    @Test
    void testStoreAndGetProducerConfig() {
        ProducerConfigDTO config = createProducerConfig();
        store.storeProducerConfig(CLIENT_ID, config);
        ProducerConfigDTO retrieved = store.getProducerConfig(CLIENT_ID);

        assertNotNull(retrieved);
        assertEquals(CLIENT_ID, retrieved.getClientId());
        assertEquals(1, store.getCacheSize());
    }

    @Test
    void testStoreAndGetConsumerConfig() {
        ConsumerConfigDTO config = createConsumerConfig();
        store.storeConsumerConfig(CLIENT_ID, config);
        ConsumerConfigDTO retrieved = store.getConsumerConfig(CLIENT_ID);

        assertNotNull(retrieved);
        assertEquals(CLIENT_ID, retrieved.getClientId());
        assertEquals(1, store.getCacheSize());
    }

    @Test
    void testGetNonExistentConfig() {
        assertNull(store.getProducerConfig("NON_EXISTENT"));
        assertNull(store.getConsumerConfig("NON_EXISTENT"));
        assertEquals(0, store.getCacheSize());
    }

    @Test
    void testClearCache() {
        store.storeProducerConfig(CLIENT_ID, createProducerConfig());
        store.storeConsumerConfig(CLIENT_ID, createConsumerConfig());
        assertEquals(2, store.getCacheSize());

        store.clearCache();

        assertEquals(0, store.getCacheSize());
        assertNull(store.getProducerConfig(CLIENT_ID));
        assertNull(store.getConsumerConfig(CLIENT_ID));
    }

    @Test
    void testOverwriteConfig() {
        ProducerConfigDTO original = createProducerConfig();
        original.setClientId("ORIGINAL");
        store.storeProducerConfig(CLIENT_ID, original);

        ProducerConfigDTO updated = createProducerConfig();
        updated.setClientId("UPDATED");
        store.storeProducerConfig(CLIENT_ID, updated);

        assertEquals(1, store.getCacheSize());
        assertEquals("UPDATED", store.getProducerConfig(CLIENT_ID).getClientId());
    }

    @Test
    void testSeparateNamespaces() {
        store.storeProducerConfig(CLIENT_ID, createProducerConfig());
        store.storeConsumerConfig(CLIENT_ID, createConsumerConfig());

        assertEquals(2, store.getCacheSize());
        assertNotNull(store.getProducerConfig(CLIENT_ID));
        assertNotNull(store.getConsumerConfig(CLIENT_ID));
    }

    @Test
    void testNullClientId() {
        store.storeProducerConfig(null, createProducerConfig());
        ProducerConfigDTO retrieved = store.getProducerConfig(null);

        assertNotNull(retrieved);
        assertEquals(CLIENT_ID, retrieved.getClientId());
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        int threads = 5;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final int id = i;
            executor.submit(() -> {
                performOperations(id, latch);
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(store.getCacheSize() > 0);
    }

    private void performOperations(int threadId, CountDownLatch latch) {
        for (int i = 0; i < 50; i++) {
            String clientId = "CLIENT_" + threadId + "_" + i;
            store.storeProducerConfig(clientId, createProducerConfig());
            store.getProducerConfig(clientId);
        }
        latch.countDown();
    }

    private ProducerConfigDTO createProducerConfig() {
        ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }

    private ConsumerConfigDTO createConsumerConfig() {
        ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }
}