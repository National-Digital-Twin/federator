package uk.gov.dbt.ndtp.federator.client.storage.impl;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.move;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;

/**
 * Local storage implementation. No remote upload is performed; returns the local path.
 */
@Slf4j
public class LocalReceivedFileStorage implements ReceivedFileStorage {

    /**
     * Returns a {@link StoredFileResult} that points to the local file; no remote side effects occur.
     *
     * @param localFile absolute path of the assembled file on the local filesystem
     * @param originalFileName original file name from the stream (unused for local storage)
     * @return {@link StoredFileResult} containing the absolute local path; {@code remoteUri} is {@code null}
     */
    @Override
    public StoredFileResult store(Path localFile, String originalFileName, String destination) {
        // If a destination path is provided (e.g., "docs/guidelines.md"), move/rename the file exactly to that path.
        try {
            if (destination != null && !destination.isBlank()) {
                Path targetPath = Path.of(destination).toAbsolutePath();
                Path parent = targetPath.getParent();
                if (parent != null) {
                    createDirectories(parent);
                }
                // Move (rename) the assembled file into the destination file path
                moveWithFallback(localFile, targetPath);
                log.info("Moved file to destination path: {}", targetPath);
                return new StoredFileResult(targetPath.toAbsolutePath(), null);
            }
        } catch (Exception ex) {
            log.warn("Failed to move file to destination '{}'; keeping at {}", destination, localFile, ex);
        }
        log.debug("LOCAL storage provider selected; keeping file locally at {}", localFile);
        return new StoredFileResult(localFile.toAbsolutePath(), null);
    }

    /**
     * Attempts to move the file atomically; if not supported by the filesystem, retries without ATOMIC_MOVE.
     * Extracted for testability and to reduce nested try/catch in {@link #store(Path, String, String)}.
     */
    void moveWithFallback(Path source, Path target) throws IOException {
        try {
            move(source, target, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (IOException e) {
            // Retry without ATOMIC_MOVE if filesystem doesn't support it
            move(source, target, REPLACE_EXISTING);
        }
    }
}
