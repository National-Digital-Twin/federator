package uk.gov.dbt.ndtp.federator.exceptions;

/**
 * Exception thrown when application configuration is missing or invalid.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationException(String propertyName, String expectedValue) {
        super(String.format("Configuration error: Property '%s' must be set to '%s'", propertyName, expectedValue));
    }
}
