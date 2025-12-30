package uk.gov.dbt.ndtp.federator.client.storage;

import java.nio.file.Path;

/**
 * Abstraction to store an assembled file to the desired destination (LOCAL or remote like S3).
 * Implementations should be stateless and read required configuration from PropertyUtil or injected clients.
 */
public interface ReceivedFileStorage {
    /**
     * Stores the assembled file according to the configured storage provider.
     *
     * @param localFile absolute path to assembled file on local filesystem
     * @param originalFileName original file name from stream (used for remote key naming)
     * @param destination destination provided by caller. Semantics:
     *                    - For LOCAL provider: interpreted as a target file path (e.g. {@code docs/guidelines.md}); file may be moved/renamed there.
     *                    - For S3 provider: interpreted as the object key or key prefix (no {@code s3://}); bucket always comes from {@code files.s3.bucket}.
     * @return result including local path and optional remote URI
     */
    StoredFileResult store(Path localFile, String originalFileName, String destination);
}
