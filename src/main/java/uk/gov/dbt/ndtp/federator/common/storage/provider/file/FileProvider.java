package uk.gov.dbt.ndtp.federator.common.storage.provider.file;

import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

/**
 * Contract for components capable of fetching a file stream and its size from a remote source.
 */
public interface FileProvider {

    /**
     * Fetches a file described by the given request, returning an input stream and the known size.
     * Implementations should throw a domain-specific runtime exception when the file is not found
     * or cannot be accessed.
     *
     * @param request description of the remote file to retrieve
     * @return {@link FileTransferResult} containing an {@code InputStream} and the content length
     */
    FileTransferResult get(FileTransferRequest request);
}
