package uk.gov.dbt.ndtp.federator.client.storage;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Result of storing a received file. Always contains the local path where the file is assembled.
 * Optionally contains a remote URI (e.g., s3://bucket/key) when uploaded to a remote store.
 */
public record StoredFileResult(Path localPath, String remoteUri) {
    /**
     * Convenience accessor that wraps the remote URI (if present) in an {@link Optional}.
     *
     * @return {@code Optional.of(remoteUri)} when a remote location exists, otherwise {@code Optional.empty()}
     */
    public Optional<String> remoteUriOpt() {
        return Optional.ofNullable(remoteUri);
    }
}
