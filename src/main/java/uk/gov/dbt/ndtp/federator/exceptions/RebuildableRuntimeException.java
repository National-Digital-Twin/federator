package uk.gov.dbt.ndtp.federator.exceptions;

/**
* Abstract base for runtime exceptions that enforce rebuildability.
* Subclasses must implement {@link #rebuild(String, Throwable)} to return
* a new instance of themselves with the given message and cause.
*/
public abstract class RebuildableRuntimeException extends RuntimeException {

    private final String componentName;
    private final String operation;
    private final String targetId;

    public RebuildableRuntimeException(String message) {
        super(message);
        this.componentName = null;
        this.operation = null;
        this.targetId = null;
    }
    public RebuildableRuntimeException(String message, Throwable cause) {
        super(message, cause);
        this.componentName = null;
        this.operation = null;
        this.targetId = null;
    }
    public RebuildableRuntimeException(String message, Throwable cause, String componentName, String operation, String targetId) {
        super(message, cause);
        this.componentName = componentName;
        this.operation = operation;
        this.targetId = targetId;
    }
    public abstract RebuildableRuntimeException rebuild(String message, Throwable cause);

    public String getComponentName() { return this.componentName; }

    public String getOperation() { return this.operation; }

    public String getTargetId() { return this.targetId; }
}
