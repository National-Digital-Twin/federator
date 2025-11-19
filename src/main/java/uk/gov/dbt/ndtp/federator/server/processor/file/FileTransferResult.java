package uk.gov.dbt.ndtp.federator.server.processor.file;

import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents the result of a file fetch operation.
 * Wraps the input stream so it can be safely closed using try-with-resources.
 */
@Slf4j
public record FileTransferResult(InputStream stream, long fileSize) implements AutoCloseable {

    @Override
    public void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                log.warn("Failed to close stream: {}", e.getMessage());
            }
        }
    }
}
