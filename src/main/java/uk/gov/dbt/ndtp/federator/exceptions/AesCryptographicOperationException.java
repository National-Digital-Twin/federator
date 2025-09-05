// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

package uk.gov.dbt.ndtp.federator.exceptions;

/**
 * Exception for AES cryptographic operation failures
 */
public class AesCryptographicOperationException extends RuntimeException {

    public AesCryptographicOperationException(String message) {
        super(message);
    }

    public AesCryptographicOperationException(String message, Throwable e) {
        super(message, e);
    }
}
