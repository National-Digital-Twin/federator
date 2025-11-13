package uk.gov.dbt.ndtp.federator.client.storage;

import java.nio.file.Path;

/**
 * Abstraction to store an assembled file to the desired destination (LOCAL or remote like S3).
 * Implementations should be stateless and read required configuration from PropertyUtil or injected clients.
 */
public interface ReceivedFileStorage {
    /**
     * Stores the assembled file according to the configured storage provider.
     * Implementations must not delete or move the provided local file; it remains the source of truth.
     *
     * @param localFile absolute path to assembled file on local filesystem
     * @param originalFileName original file name from stream (used for remote key naming)
     * @return result including local path and optional remote URI
     */
    StoredFileResult store(Path localFile, String originalFileName);
}
