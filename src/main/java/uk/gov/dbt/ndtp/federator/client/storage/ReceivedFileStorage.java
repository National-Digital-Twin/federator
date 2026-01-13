package uk.gov.dbt.ndtp.federator.client.storage;

import java.nio.file.Path;
import static java.nio.file.Files.deleteIfExists;

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

    // -------- Default helpers shared by implementations --------

    /**
     * Resolves the remote object key/path to use for storage providers that require one (e.g., S3/Azure).
     *
     * <p>Rules:
     * <ul>
     *   <li>If {@code destination} ends with a slash, it is treated as a prefix and the sanitized
     *       {@code originalFileName} is appended.</li>
     *   <li>If {@code destination} is a non-blank path not ending with a slash, it is used as-is
     *       (after normalization that strips leading slashes).</li>
     *   <li>If {@code destination} is blank, the sanitized {@code originalFileName} is used.</li>
     * </ul>
     *
     * @param destination optional destination path or prefix
     * @param originalFileName original file name from the stream
     * @return normalized object key/path suitable for the remote provider
     */
    default String resolveKey(String destination, String originalFileName) {
        if (destination != null && !destination.isBlank()) {
            String d = destination.trim();
            return d.endsWith("/") ? buildKey(d, sanitize(originalFileName)) : normalizeKey(d);
        }
        return sanitize(originalFileName);
    }

    /**
     * Attempts to delete a temporary local file, suppressing any exceptions.
     *
     * @param localFile path of the temporary local file to delete
     */
    default void deleteLocalTempQuietly(Path localFile) {
        try {
            boolean deleted = deleteIfExists(localFile);
            // keep logging minimal in default helpers; implementations may log around calls if needed
        } catch (Exception ignored) {
            // swallow
        }
    }

    /**
     * Returns only the base file name, removing any leading directory components.
     *
     * @param name a file name that may contain path separators
     * @return just the file name component without directories
     */
    default String sanitize(String name) {
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    /**
     * Concatenates a prefix (directory-like) and file name, ensuring exactly one slash between them
     * and stripping any leading slash from the prefix.
     *
     * @param prefix path-like prefix which may start/omit a trailing slash
     * @param fileName file name to append
     * @return {@code prefix + fileName} with normalized slashes
     */
    default String buildKey(String prefix, String fileName) {
        String p = prefix == null ? "" : prefix.trim();
        if (p.startsWith("/")) p = p.substring(1);
        if (!p.isEmpty() && !p.endsWith("/")) p = p + "/";
        return p + fileName;
    }

    /**
     * Normalizes a key by removing any leading slashes.
     *
     * @param key key/path to normalize
     * @return key without leading slashes
     */
    default String normalizeKey(String key) {
        String k = key.trim();
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        return k;
    }
}
