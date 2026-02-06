package uk.gov.dbt.ndtp.federator.common.exception;

/**
 * Exception thrown when file transfer validation fails or file operations encounter errors.
 */
public class FileTransferException extends RuntimeException {
    public FileTransferException(String message) {
        super(message);
    }

    public FileTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
