package uk.gov.dbt.ndtp.federator.exceptions;

import lombok.Getter;

/**
 * Custom exception for Management Node related errors.
 */
@Getter
public class ManagementNodeException extends Exception {

    private final String errorCode;
    private final int httpStatusCode;

    public ManagementNodeException(String message) {
        super(message);
        this.errorCode = null;
        this.httpStatusCode = 0;
    }

    public ManagementNodeException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.httpStatusCode = 0;
    }

    public ManagementNodeException(String message, String errorCode, int httpStatusCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }

    public ManagementNodeException(String message, Throwable cause, String errorCode, int httpStatusCode) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }
}
