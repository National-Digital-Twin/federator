package uk.gov.dbt.ndtp.federator.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryConfigurationStore.
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-08-20
 */
class InMemoryConfigurationStoreTest {

    private static final String CLIENT_ID = "FEDERATOR_BCC";
    private static final String ANOTHER_CLIENT = "FEDERATOR_XYZ";
    private static final long SHORT_TTL = 1; // 1 second for testing

    private InMemoryConfigurationStore store;

    /**
     * Sets up test fixtures before each test.
     */
    @BeforeEach
    void setUp() {
        store = new InMemoryConfigurationStore();
    }

    /**
     * Tests storing and retrieving producer configuration.
     */
    @Test
    void testStoreAndGetProducerConfig() {
        final ProducerConfigDTO config = createProducerConfig(CLIENT_ID);

        store.storeProducerConfig(CLIENT_ID, config);

        final ProducerConfigDTO retrieved = store.getProducerConfig(CLIENT_ID);
        assertNotNull(retrieved);
        assertEquals(CLIENT_ID, retrieved.getClientId());
        assertEquals(1, store.getCacheSize());
    }

    /**
     * Tests storing and retrieving consumer configuration.
     */
    @Test
    void testStoreAndGetConsumerConfig() {
        final ConsumerConfigDTO config = createConsumerConfig(CLIENT_ID);

        store.storeConsumerConfig(CLIENT_ID, config);

        final ConsumerConfigDTO retrieved = store.getConsumerConfig(CLIENT_ID);
        assertNotNull(retrieved);
        assertEquals(CLIENT_ID, retrieved.getClientId());
        assertEquals(1, store.getCacheSize());
    }

    /**
     * Tests retrieving non-existent configuration returns null.
     */
    @Test
    void testGetNonExistentConfig() {
        assertNull(store.getProducerConfig("NON_EXISTENT"));
        assertNull(store.getConsumerConfig("NON_EXISTENT"));
        assertEquals(0, store.getCacheSize());
    }

    /**
     * Tests cache expiration and automatic eviction.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCacheExpirationWithEviction() throws InterruptedException {
        store = new InMemoryConfigurationStore(SHORT_TTL);
        final ProducerConfigDTO config = createProducerConfig(CLIENT_ID);

        store.storeProducerConfig(CLIENT_ID, config);
        assertNotNull(store.getProducerConfig(CLIENT_ID));
        assertEquals(1, store.getCacheSize());

        Thread.sleep((SHORT_TTL + 1) * 1000);

        assertNull(store.getProducerConfig(CLIENT_ID));
        assertEquals(0, store.getCacheSize()); // Should be evicted
    }

    /**
     * Tests clearing the cache.
     */
    @Test
    void testClearCache() {
        store.storeProducerConfig(CLIENT_ID, createProducerConfig(CLIENT_ID));
        store.storeConsumerConfig(CLIENT_ID, createConsumerConfig(CLIENT_ID));
        store.storeProducerConfig(ANOTHER_CLIENT, createProducerConfig(ANOTHER_CLIENT));

        assertEquals(3, store.getCacheSize());

        store.clearCache();

        assertEquals(0, store.getCacheSize());
        assertNull(store.getProducerConfig(CLIENT_ID));
        assertNull(store.getConsumerConfig(CLIENT_ID));
    }

    /**
     * Tests storing multiple client configurations.
     */
    @Test
    void testMultipleClients() {
        store.storeProducerConfig(CLIENT_ID, createProducerConfig(CLIENT_ID));
        store.storeProducerConfig(ANOTHER_CLIENT, createProducerConfig(ANOTHER_CLIENT));
        store.storeConsumerConfig(CLIENT_ID, createConsumerConfig(CLIENT_ID));

        assertEquals(3, store.getCacheSize());
        assertNotNull(store.getProducerConfig(CLIENT_ID));
        assertNotNull(store.getProducerConfig(ANOTHER_CLIENT));
        assertNotNull(store.getConsumerConfig(CLIENT_ID));
    }

    /**
     * Tests overwriting existing configuration.
     */
    @Test
    void testOverwriteConfig() {
        final ProducerConfigDTO original = createProducerConfig(CLIENT_ID);
        original.setClientId("ORIGINAL");
        store.storeProducerConfig(CLIENT_ID, original);

        final ProducerConfigDTO updated = createProducerConfig(CLIENT_ID);
        updated.setClientId("UPDATED");
        store.storeProducerConfig(CLIENT_ID, updated);

        assertEquals(1, store.getCacheSize());
        final ProducerConfigDTO retrieved = store.getProducerConfig(CLIENT_ID);
        assertEquals("UPDATED", retrieved.getClientId());
    }

    /**
     * Tests thread safety with concurrent operations.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testThreadSafety() throws InterruptedException {
        final int threadCount = 10;
        final int operationsPerThread = 100;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                performConcurrentOps(threadId, operationsPerThread);
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(store.getCacheSize() > 0);
    }

    /**
     * Tests separate namespaces for producer and consumer configs.
     */
    @Test
    void testSeparateNamespaces() {
        store.storeProducerConfig(CLIENT_ID, createProducerConfig(CLIENT_ID));
        store.storeConsumerConfig(CLIENT_ID, createConsumerConfig(CLIENT_ID));

        assertEquals(2, store.getCacheSize());

        final ProducerConfigDTO producer = store.getProducerConfig(CLIENT_ID);
        final ConsumerConfigDTO consumer = store.getConsumerConfig(CLIENT_ID);

        assertNotNull(producer);
        assertNotNull(consumer);
        assertNotEquals(producer, consumer);
    }

    /**
     * Tests default TTL constructor.
     */
    @Test
    void testDefaultTTL() {
        final InMemoryConfigurationStore defaultStore =
                new InMemoryConfigurationStore();

        defaultStore.storeProducerConfig(CLIENT_ID, createProducerConfig(CLIENT_ID));
        assertNotNull(defaultStore.getProducerConfig(CLIENT_ID));
        assertEquals(1, defaultStore.getCacheSize());
    }

    /**
     * Tests null client ID handling.
     */
    @Test
    void testNullClientId() {
        store.storeProducerConfig(null, createProducerConfig("NULL_CLIENT"));

        final ProducerConfigDTO retrieved = store.getProducerConfig(null);
        assertNotNull(retrieved);
        assertEquals("NULL_CLIENT", retrieved.getClientId());
    }

    /**
     * Tests expired entry removal on access.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExpiredEntryRemoval() throws InterruptedException {
        store = new InMemoryConfigurationStore(SHORT_TTL);

        store.storeProducerConfig(CLIENT_ID, createProducerConfig(CLIENT_ID));
        store.storeConsumerConfig(CLIENT_ID, createConsumerConfig(CLIENT_ID));
        assertEquals(2, store.getCacheSize());

        Thread.sleep((SHORT_TTL + 1) * 1000);

        assertNull(store.getProducerConfig(CLIENT_ID));
        assertEquals(1, store.getCacheSize()); // One expired entry removed

        assertNull(store.getConsumerConfig(CLIENT_ID));
        assertEquals(0, store.getCacheSize()); // Both expired entries removed
    }

    /**
     * Tests cache size calculation.
     */
    @Test
    void testGetCacheSize() {
        assertEquals(0, store.getCacheSize());

        store.storeProducerConfig("CLIENT1", createProducerConfig("CLIENT1"));
        assertEquals(1, store.getCacheSize());

        store.storeConsumerConfig("CLIENT1", createConsumerConfig("CLIENT1"));
        assertEquals(2, store.getCacheSize());

        store.storeProducerConfig("CLIENT2", createProducerConfig("CLIENT2"));
        assertEquals(3, store.getCacheSize());

        store.clearCache();
        assertEquals(0, store.getCacheSize());
    }

    /**
     * Performs concurrent operations for thread safety test.
     *
     * @param threadId thread identifier
     * @param operations number of operations
     */
    private void performConcurrentOps(final int threadId, final int operations) {
        for (int i = 0; i < operations; i++) {
            final String clientId = "CLIENT_" + threadId + "_" + i;

            if (i % 2 == 0) {
                store.storeProducerConfig(clientId, createProducerConfig(clientId));
                store.getProducerConfig(clientId);
            } else {
                store.storeConsumerConfig(clientId, createConsumerConfig(clientId));
                store.getConsumerConfig(clientId);
            }

            if (i % 20 == 0) {
                store.getCacheSize();
            }
        }
    }

    /**
     * Creates a mock producer configuration.
     *
     * @param clientId client identifier
     * @return mock ProducerConfigDTO
     */
    private ProducerConfigDTO createProducerConfig(final String clientId) {
        final ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(clientId);
        return config;
    }

    /**
     * Creates a mock consumer configuration.
     *
     * @param clientId client identifier
     * @return mock ConsumerConfigDTO
     */
    private ConsumerConfigDTO createConsumerConfig(final String clientId) {
        final ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(clientId);
        return config;
    }
}