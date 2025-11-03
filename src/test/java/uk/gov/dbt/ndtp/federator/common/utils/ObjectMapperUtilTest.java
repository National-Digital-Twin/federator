// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted,
// enhanced, and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ObjectMapperUtilTest {

    static class SimpleDto {
        public String name;
    }

    @Test
    void getInstance_returnsSingleton() {
        ObjectMapper m1 = ObjectMapperUtil.getInstance();
        ObjectMapper m2 = ObjectMapperUtil.getInstance();
        assertNotNull(m1, "ObjectMapper instance should not be null");
        assertSame(m1, m2, "ObjectMapperUtil should return the same singleton instance");
    }

    @Test
    void getInstance_isThreadSafeSingleton() throws ExecutionException, InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ObjectMapper>> tasks = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                tasks.add(ObjectMapperUtil::getInstance);
            }
            List<Future<ObjectMapper>> results = pool.invokeAll(tasks);
            ObjectMapper first = results.get(0).get();
            for (Future<ObjectMapper> f : results) {
                assertSame(first, f.get(), "All threads should receive the same ObjectMapper instance");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void objectMapper_isConfiguredToIgnoreUnknownProperties_flagLevel() {
        ObjectMapper mapper = ObjectMapperUtil.getInstance();
        assertFalse(
                mapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                "FAIL_ON_UNKNOWN_PROPERTIES should be disabled");
    }

    @Test
    void objectMapper_canDeserializeWithUnknownProperties() {
        ObjectMapper mapper = ObjectMapperUtil.getInstance();
        String json = "{\"name\":\"Alice\",\"unknown\":123}";
        assertDoesNotThrow(() -> {
            SimpleDto dto = mapper.readValue(json, SimpleDto.class);
            assertEquals("Alice", dto.name);
        });
    }

    @Test
    void constructor_isInaccessibleAndThrows() throws Exception {
        Constructor<ObjectMapperUtil> ctor = ObjectMapperUtil.class.getDeclaredConstructor();
        assertFalse(ctor.canAccess(null));
        ctor.setAccessible(true);
        InvocationTargetException ite = assertThrows(InvocationTargetException.class, ctor::newInstance);
        Throwable cause = ite.getCause();
        assertNotNull(cause);
        assertInstanceOf(UnsupportedOperationException.class, cause);
        assertEquals("Utility class cannot be instantiated", cause.getMessage());
    }
}
