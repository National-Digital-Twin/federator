package uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileProvider;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileTransferResult;

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
}
