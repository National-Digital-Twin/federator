package uk.gov.dbt.ndtp.federator.processor.file.fetcher;

import uk.gov.dbt.ndtp.federator.model.SourceType;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.client.AzureBlobClientFactory;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.client.S3ClientFactory;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl.AzureFileFetcher;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl.LocalFileFetcher;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl.S3FileFetcher;

public class FileFetcherFactory {
    private FileFetcherFactory() {}

    public static FileFetcher getFetcher(SourceType sourceType) {
        return switch (sourceType) {
            case S3 -> new S3FileFetcher(S3ClientFactory.getClient());
            case AZURE -> new AzureFileFetcher(AzureBlobClientFactory.getClient());
            case LOCAL -> new LocalFileFetcher();
        };
    }
}
