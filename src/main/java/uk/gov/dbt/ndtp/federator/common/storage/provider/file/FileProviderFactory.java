package uk.gov.dbt.ndtp.federator.common.storage.provider.file;

import uk.gov.dbt.ndtp.federator.common.model.SourceType;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.AzureBlobClientFactory;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.S3ClientFactory;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.impl.AzureFileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.impl.LocalFileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.impl.S3FileProvider;

/**
 * Factory that returns a {@link FileProvider} implementation based on a {@link SourceType}.
 */
public class FileProviderFactory {
    private FileProviderFactory() {}

    /**
     * Returns a file provider suitable for the given source type.
     *
     * @param sourceType the remote source type (S3, AZURE, LOCAL)
     * @return a {@link FileProvider} capable of fetching from that source
     */
    public static FileProvider getProvider(SourceType sourceType) {
        return switch (sourceType) {
            case S3 -> new S3FileProvider(S3ClientFactory.getClient());
            case AZURE -> new AzureFileProvider(AzureBlobClientFactory.getClient());
            case LOCAL -> new LocalFileProvider();
        };
    }
}
