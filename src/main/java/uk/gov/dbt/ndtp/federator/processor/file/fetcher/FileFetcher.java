package uk.gov.dbt.ndtp.federator.processor.file.fetcher;

import uk.gov.dbt.ndtp.federator.model.FileFetchRequest;

public interface FileFetcher {
    FileFetchResult fetch(FileFetchRequest request);
}
