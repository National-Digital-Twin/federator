package uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.model.FileFetchRequest;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetchResult;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetcher;

public class LocalFileFetcher implements FileFetcher {

    @Override
    public FileFetchResult fetch(FileFetchRequest request) {
        try {
            File file = new File(request.path());
            return new FileFetchResult(new FileInputStream(file), file.length());
        } catch (IOException e) {
            throw new FileFetcherException("Failed to fetch local file: " + request.path(), e);
        }
    }
}
