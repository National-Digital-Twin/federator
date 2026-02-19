// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropertyUtilTest {

    @TempDir
    File tempDir;

    private static final String MISSING_FILE = "missing.properties";
    private static final String VALID_FILE = "test.properties";

    @BeforeEach
    void prepare() {
        PropertyUtil.clear();
        PropertyUtil.init(VALID_FILE);
    }

    @Test
    void testInitializePropertiesFromResource() {
        boolean result = PropertyUtil.initializeProperties();
        assertTrue(result);
    }

    @Test
    void testInitWithFile() throws IOException {
        PropertyUtil.clear();
        File propFile = new File(tempDir, "test-file.properties");
        Properties props = new Properties();
        props.setProperty("test.key", "test.value");
        try (FileOutputStream out = new FileOutputStream(propFile)) {
            props.store(out, null);
        }

        PropertyUtil.init(propFile);
        assertEquals("test.value", PropertyUtil.getPropertyValue("test.key"));
    }

    @Test
    void testGetPropertyValues_more() {
        PropertyUtil.clear();
        PropertyUtil.init(VALID_FILE);
        Properties props = new Properties();
        props.setProperty("string.key", "value");
        props.setProperty("int.key", "123");
        props.setProperty("long.key", "456");
        props.setProperty("bool.key", "true");
        props.setProperty("duration.key", "PT1M");

        PropertyUtil.getInstance().properties.putAll(props);
        PropertyUtil.overrideSystemProperties(PropertyUtil.getInstance().properties);

        assertEquals("value", PropertyUtil.getPropertyValue("string.key"));
        assertEquals("default", PropertyUtil.getPropertyValue("nonexistent", "default"));
        assertEquals(123, PropertyUtil.getPropertyIntValue("int.key"));
        assertEquals(456, PropertyUtil.getPropertyLongValue("long.key"));
        assertEquals(true, PropertyUtil.getPropertyBooleanValue("bool.key"));
        assertEquals(Duration.ofMinutes(1), PropertyUtil.getPropertyDurationValue("duration.key"));
    }

    @Test
    void test_getInstance_uninitialisedThrowsException() {
        // given
        PropertyUtil.clear();
        // when
        // then
        assertThrows(PropertyUtil.PropertyUtilException.class, PropertyUtil::getInstance);
    }

    @Test
    void test_init_missingStream() {
        // given
        PropertyUtil.clear();
        // when
        // then
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.init(MISSING_FILE));
    }

    @Test
    void test_getInstance_initialisedAndOverride() {
        // given
        PropertyUtil.clear();
        String expected = "overridden";
        System.setProperty("testValue", expected);
        // when
        PropertyUtil.init(VALID_FILE);
        String actual = PropertyUtil.getPropertyValue("testValue");
        // then
        assertDoesNotThrow(PropertyUtil::getInstance);
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyIntValue_valid() {
        // given
        int expected = 10;
        // when
        int actual = PropertyUtil.getPropertyIntValue("intValue");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyIntValue_ignoreDefaultWhenAvailable() {
        // given
        int expected = 10;
        // when
        int actual = PropertyUtil.getPropertyIntValue("intValue", "5");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyIntValue_useDefaultWhenMissing() {
        // given
        int expected = 5;
        // when
        int actual = PropertyUtil.getPropertyIntValue("missingValue", "5");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyIntValue_missingValue() {
        // given
        // when
        // then
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertyIntValue("missingValue"));
    }

    @Test
    void test_getPropertyLongValue_valid() {
        // given
        Long expected = 20L;
        // when
        Long actual = PropertyUtil.getPropertyLongValue("longValue");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyLongValue_ignoreDefaultWhenAvailable() {
        // given
        Long expected = 20L;
        // when
        Long actual = PropertyUtil.getPropertyLongValue("longValue", "5");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyBooleanValue_valid() {
        // given
        boolean expected = false;
        // when
        boolean actual = PropertyUtil.getPropertyBooleanValue("booleanValue");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyBooleanValue_ignoreDefaultWhenAvailable() {
        // given
        boolean expected = false;
        // when
        boolean actual = PropertyUtil.getPropertyBooleanValue("booleanValue", "true");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyBooleanValue_useDefaultWhenMissing() {
        // given
        boolean expected = true;
        // when
        boolean actual = PropertyUtil.getPropertyBooleanValue("missingValue", "true");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyBooleanValue_missingValue() {
        // given
        // when
        // then
        assertThrows(
                PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertyBooleanValue("missingValue"));
    }

    @Test
    void test_getPropertyLongValue_useDefaultWhenMissing() {
        // given
        Long expected = 5L;
        // when
        Long actual = PropertyUtil.getPropertyLongValue("missingValue", "5");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyLongValue_missingValue() {
        // given
        // when
        // then
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertyLongValue("missingValue"));
    }

    @Test
    void test_getPropertyDurationValue_valid() {
        // given
        Duration expected = Duration.ofSeconds(1L);
        // when
        Duration actual = PropertyUtil.getPropertyDurationValue("durationValue");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyDurationValue_ignoreDefaultWhenAvailable() {
        // given
        Duration expected = Duration.ofSeconds(1L);
        // when
        Duration actual = PropertyUtil.getPropertyDurationValue("durationValue", "PT2S");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyDurationValue_useDefaultWhenMissing() {
        // given
        Duration expected = Duration.ofSeconds(2L);
        // when
        Duration actual = PropertyUtil.getPropertyDurationValue("missingValue", "PT2S");
        // then
        assertEquals(expected, actual);
    }

    @Test
    void test_getPropertyDurationValue_missingValue() {
        // given
        // when
        // then
        assertThrows(
                PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertyDurationValue("missingValue"));
    }

    @Test
    void test_getPropertyFileValue_missing() {
        // given
        // when
        // then
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertyFileValue(MISSING_FILE));
    }

    @Test
    void test_getPropertyFileValue_null() {
        // given
        // when
        // then
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertyFileValue(null));
    }

    @Test
    void test_getPropertyFileValue_valid() {
        // given
        // when
        // then
        File result = PropertyUtil.getPropertyFileValue("file.location");
        assertEquals(VALID_FILE, result.getName());
    }

    @Test
    void test_getByPrefix_null() {
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getByPrefix(null));
    }

    @Test
    void test_getByPrefix_valid() {
        Properties found = PropertyUtil.getByPrefix("kafka.");

        Properties expected = new Properties();
        expected.putAll(Map.of(
                "kafka.bootstrapServers", "test",
                "kafka.consumerGroup", "test",
                "kafka.pollRecords", "PT10S"));

        assertEquals(expected, found);
    }

    @Test
    void test_initTwice_ThrowsException() {
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.init(VALID_FILE));
    }

    @Test
    void test_initFileTwice_ThrowsException() throws IOException {
        File propFile = new File(tempDir, "test-file-twice.properties");
        propFile.createNewFile();
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.init(propFile));
    }

    @Test
    void test_getPropertiesFromFilePath_AbsolutePath() throws IOException {
        File propFile = new File(tempDir, "absolute.properties");
        Properties props = new Properties();
        props.setProperty("nested.key", "nested.value");
        try (FileOutputStream out = new FileOutputStream(propFile)) {
            props.store(out, null);
        }

        PropertyUtil.getInstance().properties.setProperty("absolute.path.key", propFile.getAbsolutePath());
        Properties nested = PropertyUtil.getPropertiesFromFilePath("absolute.path.key");
        assertEquals("nested.value", nested.getProperty("nested.key"));
    }

    @Test
    void test_getPropertiesFromFilePath_Classpath() {
        PropertyUtil.getInstance().properties.setProperty("classpath.key", VALID_FILE);
        Properties nested = PropertyUtil.getPropertiesFromFilePath("classpath.key");
        assertFalse(nested.isEmpty());
    }

    @Test
    void testPropertyUtilException_constructors() {
        PropertyUtil.PropertyUtilException e1 = new PropertyUtil.PropertyUtilException("msg");
        assertEquals("msg", e1.getMessage());

        PropertyUtil.PropertyUtilException e2 = new PropertyUtil.PropertyUtilException(new RuntimeException("cause"));
        assertTrue(e2.getCause() instanceof RuntimeException);

        PropertyUtil.PropertyUtilException e3 =
                new PropertyUtil.PropertyUtilException("msg", new RuntimeException("cause"));
        assertEquals("msg", e3.getMessage());
        assertTrue(e3.getCause() instanceof RuntimeException);
    }

    @Test
    void test_init_InvalidResource() {
        PropertyUtil.clear();
        assertThrows(PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.init("nonexistent-resource"));
    }

    @Test
    void test_getPropertiesFromFilePath_InvalidFile() {
        PropertyUtil.getInstance().properties.setProperty("invalid.path", "/non/existent/path/file.properties");
        assertThrows(
                PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertiesFromFilePath("invalid.path"));
    }

    @Test
    void test_getPropertiesFromFilePath_Invalid() {
        PropertyUtil.getInstance().properties.setProperty("invalid.key", "nonexistent.file");
        assertThrows(
                PropertyUtil.PropertyUtilException.class, () -> PropertyUtil.getPropertiesFromFilePath("invalid.key"));
    }
}
