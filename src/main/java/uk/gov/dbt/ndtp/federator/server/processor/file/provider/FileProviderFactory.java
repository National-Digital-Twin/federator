package uk.gov.dbt.ndtp.federator.server.processor.file.provider;

import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.client.AzureBlobClientFactory;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.client.S3ClientFactory;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.impl.AzureFileProvider;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.impl.LocalFileProvider;
import uk.gov.dbt.ndtp.federator.server.processor.file.provider.impl.S3FileProvider;

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
