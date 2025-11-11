package uk.gov.dbt.ndtp.federator.exceptions;
/**
 * Exception thrown when there is an error during file assembly.
 */
public class FileAssemblyException extends RuntimeException {
    public FileAssemblyException(String message) {
        super(message);
    }

    public FileAssemblyException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileAssemblyException(Throwable cause) {
        super(cause);
    }
}
