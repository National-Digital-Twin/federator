package uk.gov.dbt.ndtp.federator.client.storage.impl;

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
    public StoredFileResult store(Path localFile, String originalFileName) {
        log.debug("LOCAL storage provider selected; keeping file locally at {}", localFile);
        return new StoredFileResult(localFile.toAbsolutePath(), null);
    }
}
