package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

public class LocalFileProvider implements FileProvider {

    /**
     * Fetches the local file specified in the FileTransferRequest.
     * @param request
     * @return
     */
    @Override
    public FileTransferResult get(FileTransferRequest request) {
        try {
            File file = new File(request.path());
            return new FileTransferResult(new FileInputStream(file), file.length());
        } catch (IOException e) {
            throw new FileFetcherException("Failed to fetch local file: " + request.path(), e);
        }
    }

    /**
     * Validates that the path points to an existing file in the local file system.
     * @param request the file transfer request containing the path to validate
     * @throws FileTransferException if the path does not exist or is not a file
     */
    @Override
    public void validatePath(FileTransferRequest request) {
        executeValidation(
                () -> {
                    File file = new File(request.path());
                    if (!file.exists()) {
                        throw new FileTransferException("Local file does not exist: " + request.path());
                    }
                    if (!file.isFile()) {
                        throw new FileTransferException("Local path is not a file: " + request.path());
                    }
                },
                "Invalid local file path: " + request.path());
    }
}
