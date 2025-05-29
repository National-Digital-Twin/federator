// SPDX-License-Identifier: Apache-2.0
package uk.gov.dbt.ndtp.federator.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class for loading properties in tests.
 */
public class CommonPropertiesLoader {

    private static final String TEST_PROPERTIES = "test.properties";
    private static final String COMMON_PROPERTIES = "common.properties";

    /**
     * Loads test properties which include all necessary values for testing.
     * Prioritizes test.properties over common.properties.
     */
    public static void loadTestProperties() {
        try {
            PropertyUtil.clear();
            File mergedFile = createMergedPropertiesFile();
            PropertyUtil.init(mergedFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test properties", e);
        }
    }

    private static File createMergedPropertiesFile() throws IOException {
        Properties merged = new Properties();

        // First load common.properties as base
        try (InputStream is = CommonPropertiesLoader.class
                .getClassLoader()
                .getResourceAsStream(COMMON_PROPERTIES)) {
            if (is != null) {
                merged.load(is);
            }
        }

        // Then override with test.properties (which has passwords)
        try (InputStream is = CommonPropertiesLoader.class
                .getClassLoader()
                .getResourceAsStream(TEST_PROPERTIES)) {
            if (is != null) {
                Properties testProps = new Properties();
                testProps.load(is);
                merged.putAll(testProps);
            }
        }

        // Create temporary file with merged properties
        File tempFile = File.createTempFile("test-merged", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            merged.forEach((key, value) -> {
                try {
                    writer.write(key + "=" + value + "\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        return tempFile;
    }
}