package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ConfigurationException;

/**
 * Factory for creating and reusing a single Azure BlobServiceClient instance.
 * The client is thread-safe and should be reused across the application.
 */
@Slf4j
public final class AzureBlobClientFactory {

    public static final String AZURE_STORAGE_CONNECTION_STRING = "azure.storage.connection.string";
    private static BlobServiceClient client = null;

    private AzureBlobClientFactory() {}

    private static BlobServiceClient createClient() {
        // Read single connection string property
        String connectionString = null;
        try {
            connectionString = PropertyUtil.getPropertyValue(AZURE_STORAGE_CONNECTION_STRING);
        } catch (Exception e) {
            log.debug("Failed to get property: azure.storage.connection.string", e);
        }

        if (connectionString == null || connectionString.isBlank()) {
            log.error("Azure Storage connection string is null or empty");
            throw new ConfigurationException("Azure Storage connection string is required. "
                    + "Set the 'azure.storage.connection.string' property in your configuration.");
        }

        log.info("Azure Storage Client initialized with connection string");

        return new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
    }

    public static synchronized BlobServiceClient getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }
}
