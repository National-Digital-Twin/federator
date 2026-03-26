package uk.gov.dbt.ndtp.federator.exceptions;
/**
 * Exception thrown when there is an error related to federator tokens.
 */
public class FederatorTokenException extends RebuildableRuntimeException {

    public FederatorTokenException(String message) {
        super(message);
    }

    public FederatorTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Rebuilds this exception with the given message and cause.
     * @param message the enriched error message
     * @param cause the original exception
     * @return a new instance of {@link FederatorTokenException}
     */
    @Override
    public FederatorTokenException rebuild(String message, Throwable cause) {
        return new FederatorTokenException(message, cause);
    }
}
