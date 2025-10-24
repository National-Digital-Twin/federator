package uk.gov.dbt.ndtp.federator.service.file;

import uk.gov.dbt.ndtp.federator.model.FileFetchRequest;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetchResult;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetcher;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetcherFactory;

public class FileFetchService {

    public FileFetchResult fetchFile(FileFetchRequest request) {
        FileFetcher fetcher = FileFetcherFactory.getFetcher(request.sourceType());
        return fetcher.fetch(request);
    }
}
