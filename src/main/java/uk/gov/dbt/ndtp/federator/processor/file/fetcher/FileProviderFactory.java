package uk.gov.dbt.ndtp.federator.processor.file.fetcher;

import uk.gov.dbt.ndtp.federator.model.SourceType;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.client.AzureBlobClientFactory;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.client.S3ClientFactory;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl.AzureFileProvider;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl.LocalFileProvider;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl.S3FileProvider;

public class FileProviderFactory {
    private FileProviderFactory() {}

    public static FileProvider getProvider(SourceType sourceType) {
        return switch (sourceType) {
            case S3 -> new S3FileProvider(S3ClientFactory.getClient());
            case AZURE -> new AzureFileProvider(AzureBlobClientFactory.getClient());
            case LOCAL -> new LocalFileProvider();
        };
    }
}
