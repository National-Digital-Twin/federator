// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class to provide a single, shared ObjectMapper instance across the application.
 */
public final class ObjectMapperUtil {

    // Initialization-on-demand holder idiom for thread-safe lazy singleton
    private static class Holder {
        private static final ObjectMapper INSTANCE = create();

        private static ObjectMapper create() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper;
        }
    }

    private ObjectMapperUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the shared ObjectMapper instance.
     *
     * @return configured singleton ObjectMapper
     */
    public static ObjectMapper getInstance() {
        return Holder.INSTANCE;
    }
}
