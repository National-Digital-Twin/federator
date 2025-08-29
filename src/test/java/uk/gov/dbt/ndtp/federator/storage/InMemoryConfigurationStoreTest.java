// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

/**
 * Unit tests for InMemoryConfigurationStore.
 */
@DisplayName("InMemoryConfigurationStore Tests")
class InMemoryConfigurationStoreTest {

    private static final String CLIENT_ID = "TEST_CLIENT";
    private static final String PRODUCER_KEY = "producer:test";
    private static final String CONSUMER_KEY = "consumer:test";
    private static final String NON_EXISTENT_KEY = "NON_EXISTENT";
    private static final String UPDATED_CLIENT_ID = "UPDATED";
    private static final String ORIGINAL_CLIENT_ID = "ORIGINAL";
    private static final int THREAD_COUNT = 5;
    private static final int OPERATIONS_PER_THREAD = 50;
    private static final int TIMEOUT_SECONDS = 5;

    private InMemoryConfigurationStore store;

    @BeforeEach
    void setUp() {
        // Initialize PropertyUtil with minimal test properties
        PropertyUtil.clear(); // Clear any previous initialization

        // Create a temporary properties file for testing
        try {
            File tempFile = File.createTempFile("test", ".properties");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write("management.node.cache.ttl.seconds=3600\n");
            }
            PropertyUtil.init(tempFile);
            tempFile.deleteOnExit();
        } catch (Exception e) {
            // If we can't create temp file, use classpath resource
            PropertyUtil.init("test.properties");
        }

        store = new InMemoryConfigurationStore();
    }

    @AfterEach
    void tearDown() {
        PropertyUtil.clear();
    }

    @Test
    @DisplayName("Store and retrieve producer config")
    void testStoreAndGetProducerConfig() {
        // Given
        final ProducerConfigDTO config = createProducerConfig();

        // When
        store.store(PRODUCER_KEY, config);
        final Optional<ProducerConfigDTO> retrieved = store.get(PRODUCER_KEY, ProducerConfigDTO.class);

        // Then
        assertTrue(retrieved.isPresent(), "Producer config should be present");
        assertEquals(CLIENT_ID, retrieved.get().getClientId(), "Client ID should match");
        assertEquals(1, store.getCacheSize(), "Cache should contain one entry");
    }

    @Test
    @DisplayName("Store and retrieve consumer config")
    void testStoreAndGetConsumerConfig() {
        // Given
        final ConsumerConfigDTO config = createConsumerConfig();

        // When
        store.store(CONSUMER_KEY, config);
        final Optional<ConsumerConfigDTO> retrieved = store.get(CONSUMER_KEY, ConsumerConfigDTO.class);

        // Then
        assertTrue(retrieved.isPresent(), "Consumer config should be present");
        assertEquals(CLIENT_ID, retrieved.get().getClientId(), "Client ID should match");
        assertEquals(1, store.getCacheSize(), "Cache should contain one entry");
    }

    @Test
    @DisplayName("Return empty for non-existent key")
    void testGetNonExistentConfig() {
        // When
        final Optional<ProducerConfigDTO> result = store.get(NON_EXISTENT_KEY, ProducerConfigDTO.class);

        // Then
        assertFalse(result.isPresent(), "Result should be empty for non-existent key");
        assertEquals(0, store.getCacheSize(), "Cache should be empty");
    }

    @Test
    @DisplayName("Clear all cached entries")
    void testClearCache() {
        // Given
        store.store(PRODUCER_KEY, createProducerConfig());
        store.store(CONSUMER_KEY, createConsumerConfig());
        assertEquals(2, store.getCacheSize(), "Cache should contain two entries");

        // When
        store.clearCache();

        // Then
        assertEquals(0, store.getCacheSize(), "Cache should be empty after clearing");
        assertFalse(
                store.get(PRODUCER_KEY, ProducerConfigDTO.class).isPresent(),
                "Producer config should not be present after clearing");
    }

    @Test
    @DisplayName("Overwrite existing entry")
    void testOverwriteConfig() {
        // Given
        final ProducerConfigDTO original = createProducerConfig();
        original.setClientId(ORIGINAL_CLIENT_ID);
        store.store(PRODUCER_KEY, original);

        // When
        final ProducerConfigDTO updated = createProducerConfig();
        updated.setClientId(UPDATED_CLIENT_ID);
        store.store(PRODUCER_KEY, updated);

        // Then
        assertEquals(1, store.getCacheSize(), "Cache should still contain one entry");
        final Optional<ProducerConfigDTO> retrieved = store.get(PRODUCER_KEY, ProducerConfigDTO.class);
        assertTrue(retrieved.isPresent(), "Updated config should be present");
        assertEquals(UPDATED_CLIENT_ID, retrieved.get().getClientId(), "Client ID should be updated");
    }

    @Test
    @DisplayName("Validate null key in store method")
    void testStoreWithNullKey() {
        // When & Then
        assertThrowsExactly(
                NullPointerException.class,
                () -> store.store(null, createProducerConfig()),
                "Should throw NPE for null key");
    }

    @Test
    @DisplayName("Validate null value in store method")
    void testStoreWithNullValue() {
        // When & Then
        assertThrowsExactly(
                NullPointerException.class, () -> store.store(PRODUCER_KEY, null), "Should throw NPE for null config");
    }

    @Test
    @DisplayName("Validate null key in get method")
    void testGetWithNullKey() {
        // When & Then
        assertThrowsExactly(
                NullPointerException.class,
                () -> store.get(null, ProducerConfigDTO.class),
                "Should throw NPE for null key in get");
    }

    @Test
    @DisplayName("Validate null class type in get method")
    void testGetWithNullClassType() {
        // When & Then
        assertThrowsExactly(
                NullPointerException.class,
                () -> store.get(PRODUCER_KEY, null),
                "Should throw NPE for null class type");
    }

    @Test
    @DisplayName("Thread safety verification")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testThreadSafety() throws InterruptedException {
        // Given
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        try {
            // When
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                executor.submit(() -> performOperations(threadId, latch));
            }

            // Then
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "All threads should complete within timeout");
            assertTrue(store.getCacheSize() > 0, "Cache should contain entries after concurrent operations");

            // Verify some entries exist
            for (int i = 0; i < THREAD_COUNT; i++) {
                final String sampleKey = "key_" + i + "_0";
                final Optional<ProducerConfigDTO> config = store.get(sampleKey, ProducerConfigDTO.class);
                assertTrue(config.isPresent(), "Sample entry from thread " + i + " should exist");
            }
        } finally {
            executor.shutdown();
            assertTrue(
                    executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Executor should terminate cleanly");
        }
    }

    private void performOperations(final int threadId, final CountDownLatch latch) {
        try {
            for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                final String key = "key_" + threadId + "_" + i;
                final ProducerConfigDTO config = createProducerConfig();
                config.setClientId(CLIENT_ID + "_" + threadId);

                store.store(key, config);

                final Optional<ProducerConfigDTO> retrieved = store.get(key, ProducerConfigDTO.class);
                assertNotNull(retrieved, "Retrieved value should not be null");
                assertTrue(retrieved.isPresent(), "Stored value should be retrievable");
            }
        } finally {
            latch.countDown();
        }
    }

    private ProducerConfigDTO createProducerConfig() {
        final ProducerConfigDTO config = new ProducerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }

    private ConsumerConfigDTO createConsumerConfig() {
        final ConsumerConfigDTO config = new ConsumerConfigDTO();
        config.setClientId(CLIENT_ID);
        return config;
    }
}
