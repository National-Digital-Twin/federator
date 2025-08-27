// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.management;

/**
 * Custom exception for Management Node data handling operations.
 * This exception is thrown when there are issues with fetching or processing
 * configuration data from the Management Node.
 */
public class ManagementNodeDataException extends Exception {

    /**
     * Constructs a new ManagementNodeDataException with the specified detail message.
     *
     * @param message the detail message
     */
    public ManagementNodeDataException(String message) {
        super(message);
    }

    /**
     * Constructs a new ManagementNodeDataException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public ManagementNodeDataException(String message, Throwable cause) {
        super(message, cause);
    }
}