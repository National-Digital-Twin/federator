package uk.gov.dbt.ndtp.federator.exceptions;
/**
 * Exception thrown when there is an error related to federator tokens.
 */
public class FederatorTokenException extends RuntimeException {

    public FederatorTokenException(String message) {
        super(message);
    }
    public FederatorTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
