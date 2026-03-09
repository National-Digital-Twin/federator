package uk.gov.dbt.ndtp.federator.exceptions;

/**
* Abstract base for runtime exceptions that enforce rebuildability.
* Subclasses must implement {@link #rebuild(String, Throwable)} to return
* a new instance of themselves with the given message and cause.
*/
public abstract class RebuildableRuntimeException extends RuntimeException {

    public String componentName;
    public String operation;
    public String targetId;

    public RebuildableRuntimeException(String message) { super(message); }
    public RebuildableRuntimeException(String message, Throwable cause) { super(message, cause); }
    public RebuildableRuntimeException(String message, Throwable cause, String componentName, String operation, String targetId) {
        super(message, cause);
        this.componentName = componentName;
        this.operation = operation;
        this.targetId = targetId;
    }
    public abstract RebuildableRuntimeException rebuild(String message, Throwable cause);
}
