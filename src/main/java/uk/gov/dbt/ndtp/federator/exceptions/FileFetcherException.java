package uk.gov.dbt.ndtp.federator.exceptions;

public class FileFetcherException extends RuntimeException {

    public FileFetcherException(String message) {
        super(message);
    }

    public FileFetcherException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileFetcherException(Throwable cause) {
        super(cause);
    }
}
