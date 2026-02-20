// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.management;

import java.io.Serial;

/**
 * Exception for Management Node communication failures.
 */
public class ManagementNodeDataException extends RuntimeException {

    /**
     * Serial version UID for serialization.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs exception with message.
     *
     * @param message error message
     */
    public ManagementNodeDataException(final String message) {
        super(message);
    }

    /**
     * Constructs exception with message and cause.
     *
     * @param message error message
     * @param cause underlying cause
     */
    public ManagementNodeDataException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
