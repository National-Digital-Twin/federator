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

import static uk.gov.dbt.ndtp.federator.FederatorServer.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to load properties from a file or resource.
 * <p>
 *   The class is a singleton and should be initialised with the {@link #init(String)} or {@link #init(File)} methods.
 *   The properties can be accessed using the {@link #getPropertyValue(String)} method.
 *   The class will throw a {@link PropertyUtilException} if the properties are not initialised or if a property is missing.
 *   The class will also throw a {@link PropertyUtilException} if the properties are initialised twice.
 *   The class will also override any properties with the same key in the system properties.
 *   The class also provides methods to get properties as int, long, boolean, duration and file.
 *   The class also provides a method to clear the properties for testing purposes.
 * </p>
 */
public class PropertyUtil {

    public static final Logger LOGGER = LoggerFactory.getLogger("PropertyUtil");

    private static PropertyUtil instance;
    private final Properties properties;

    private PropertyUtil(InputStream inputStream) {
        properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        } catch (Exception t) {
            throw new PropertyUtilException("Error loading properties from inputStream", t);
        }
        overrideSystemProperties(properties);
    }

    public static boolean initializeProperties() {
        try {
            // Check if already initialized by trying to get instance
            PropertyUtil.getInstance();
            return true;
        } catch (Exception e) {
            LOGGER.debug("Failed to get PropertyUtil instance", e);
            // Not initialized, so initialize now
            final String envProps = System.getenv(ENV_SERVER_PROPS);
            if (envProps != null) {
                final File file = new File(envProps);
                if (file.exists()) {
                    try {
                        PropertyUtil.init(file);
                        return true;
                    } catch (Exception ex) {
                        LOGGER.error("Failed to load properties from: {}", file.getPath(), ex);
                        return false;
                    }
                }
                LOGGER.warn("File specified by {} not found: {}", ENV_SERVER_PROPS, envProps);
            }

            // Try to load from classpath as last resort
            try {
                PropertyUtil.init(SERVER_PROPERTIES);
                return true;
            } catch (Exception exception) {
                LOGGER.error(
                        "Failed to load {} from classpath. " + "Ensure file exists in resources directory or "
                                + "set {} to valid file path",
                        SERVER_PROPERTIES,
                        ENV_SERVER_PROPS,
                        exception);
                return false;
            }
        }
    }

    public static void init(String resourceName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (null == instance) {
            try (InputStream resourceStream = loader.getResourceAsStream(resourceName)) {
                LOGGER.info("Loading Properties from resource: '{}'", resourceName);
                instance = new PropertyUtil(resourceStream);
            } catch (Exception t) {
                throw new PropertyUtilException("Error loading properties from resource", t);
            }
        } else {
            throw new PropertyUtilException("Cannot initialise Property Util twice");
        }
    }

    public static void init(File file) {
        if (null == instance) {
            try (InputStream inputStream = new FileInputStream(file)) {
                LOGGER.info("Loading Properties from file: '{}'", file.getPath());
                instance = new PropertyUtil(inputStream);
            } catch (Exception t) {
                throw new PropertyUtilException("Error loading properties from file", t);
            }
        } else {
            throw new PropertyUtilException("Cannot initialise Property Util twice");
        }
    }

    public static PropertyUtil getInstance() {
        if (null == instance) {
            throw new PropertyUtilException("Property Util not properly initialised");
        }
        return instance;
    }

    public static String getPropertyValue(String key) {
        return getInstance().getValue(key);
    }

    public static String getPropertyValue(String key, String defaultValue) {
        return getInstance().getValue(key, defaultValue);
    }

    public static int getPropertyIntValue(String key) {
        return Integer.parseInt(getPropertyValue(key));
    }

    public static int getPropertyIntValue(String key, String defaultValue) {
        return Integer.parseInt(getPropertyValue(key, defaultValue));
    }

    public static long getPropertyLongValue(String key) {
        return Long.parseLong(getPropertyValue(key));
    }

    public static long getPropertyLongValue(String key, String defaultValue) {
        return Long.parseLong(getPropertyValue(key, defaultValue));
    }

    public static Duration getPropertyDurationValue(String key) {
        return Duration.parse(getPropertyValue(key));
    }

    public static Duration getPropertyDurationValue(String key, String defaultValue) {
        return Duration.parse(getPropertyValue(key, defaultValue));
    }

    public static boolean getPropertyBooleanValue(String key) {
        return Boolean.parseBoolean(getPropertyValue(key));
    }

    public static boolean getPropertyBooleanValue(String key, String defaultValue) {
        return Boolean.parseBoolean(getPropertyValue(key, defaultValue));
    }

    public static File getPropertyFileValue(String key) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            return new File(loader.getResource(getPropertyValue(key)).toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new PropertyUtilException(e);
        }
    }

    /**
     * Loads a properties file specified by the property key. The value of the property can be either an absolute
     * file path or a classpath resource. The method will first attempt to load the properties from the absolute
     * file path, and if that fails, it will attempt to load it from the classpath resource.
     *
     * @param filePathKey the property key that contains the file path or classpath resource
     * @return the loaded properties
     * @throws PropertyUtilException if the properties cannot be loaded from either location
     */
    public static Properties getPropertiesFromFilePath(String filePathKey) {
        String configured = getPropertyValue(filePathKey);
        Properties nestedProperties = new Properties();

        // First try absolute path
        File absoluteFile = new File(configured);
        if (absoluteFile.isFile() && absoluteFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(absoluteFile)) {
                nestedProperties.load(fis);
                return nestedProperties;
            } catch (Exception e) {
                LOGGER.warn(
                        "Failed reading properties from absolute path '{}', attempting classpath resource",
                        absoluteFile.getAbsolutePath(),
                        e);
            }
        } else {
            LOGGER.info("Absolute path '{}' not valid, attempting classpath resource", absoluteFile.getPath());
        }

        // Fallback: try classpath resource
        try {
            File resourceFile = getPropertyFileValue(filePathKey);
            try (FileInputStream fis = new FileInputStream(resourceFile)) {
                nestedProperties.load(fis);
                return nestedProperties;
            }
        } catch (Exception e) {
            throw new PropertyUtilException(
                    "Failed to load properties for key '" + filePathKey + "' from absolute path '"
                            + absoluteFile.getPath() + "' or classpath resource '" + configured + "'",
                    e);
        }
    }

    public static Properties getByPrefix(String prefix) {
        if (prefix == null) {
            throw new PropertyUtilException("The prefix to search for must not be null");
        }
        Properties props = getInstance().properties;
        Properties found = new Properties();
        props.forEach((key, value) -> {
            if (key instanceof String name && name.startsWith(prefix)) {
                found.put(name, value);
            }
        });
        return found;
    }

    /**
     * For testing purposes
     */
    public static void clear() {
        instance = null;
    }

    public String getValue(String key) {
        String result = properties.getProperty(key);
        if (null == result) {
            throw new PropertyUtilException(String.format("Missing property: '%s'", key));
        }
        return result;
    }

    public String getValue(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private void overrideSystemProperties(Properties properties) {
        String keyset = properties.keySet().toString();
        LOGGER.info("Properties KeySet from File - [{}]", keyset);
        for (Object key : properties.keySet()) {
            String override = System.getProperty((String) key);
            if (override != null) {
                LOGGER.info("Overriding file property with system property - '{}'", key);
                properties.put(key, override);
            } else {
                LOGGER.info("Using File Property - '{}'", key);
            }
        }
    }

    public static class PropertyUtilException extends RuntimeException {
        public PropertyUtilException(String message) {
            super(message);
        }

        public PropertyUtilException(Throwable cause) {
            super(cause);
        }

        public PropertyUtilException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
