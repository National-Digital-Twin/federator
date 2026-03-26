package uk.gov.dbt.ndtp.federator.exceptions;

/**
 * Abstract base for runtime exceptions that enforce rebuildability.
 * Subclasses must implement {@link #rebuild(String, Throwable)} to return
 * a new instance of themselves with the given message and cause.
 */
public abstract class RebuildableRuntimeException extends RuntimeException {
    protected RebuildableRuntimeException(String message) {
        super(message);
    }

    protected RebuildableRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract RebuildableRuntimeException rebuild(String message, Throwable cause);
}
