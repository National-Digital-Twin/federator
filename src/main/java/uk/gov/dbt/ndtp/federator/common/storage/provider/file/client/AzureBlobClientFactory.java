package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import com.azure.core.http.HttpClient;
import com.azure.core.http.okhttp.OkHttpAsyncHttpClientBuilder;
import com.azure.identity.WorkloadIdentityCredential;
import com.azure.identity.WorkloadIdentityCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ConfigurationException;

/**
 * Factory for creating and reusing a single Azure BlobServiceClient instance.
 * The client is thread-safe and should be reused across the application.
 * Supports both connection string (for local/dev) and Service Account (for production).
 */
@Slf4j
public final class AzureBlobClientFactory {
    private static final String CONNECTION_STRING_PROPERTY = "azure.storage.connection.string";
    private static final String ENDPOINT_PROPERTY = "azure.storage.endpoint";

    private static BlobServiceClient client;

    private AzureBlobClientFactory() {}

    private static BlobServiceClient createClient() {
        // First check for connection string (for local development/emulator)
        String connectionString = null;
        HttpClient httpClient = new OkHttpAsyncHttpClientBuilder()
                .connectionTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .responseTimeout(Duration.ofMinutes(2))
                .build();
        try {
            connectionString = PropertyUtil.getPropertyValue(CONNECTION_STRING_PROPERTY);
        } catch (Exception e) {
            log.debug("Connection string property not found");
        }

        if (connectionString != null && !connectionString.isBlank()) {
            log.info("Azure Storage Client initialized with connection string (local/emulator mode)");
            return new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .httpClient(httpClient)
                    .buildClient();
        }

        // Fall back to Managed Identity authentication (for production)
        String endpoint = null;
        try {
            endpoint = PropertyUtil.getPropertyValue(ENDPOINT_PROPERTY);
        } catch (Exception e) {
            log.debug("Endpoint property not found");
        }

        if (endpoint == null || endpoint.isBlank()) {
            log.error("Azure Storage configuration not found. Need either connection string or endpoint.");
            throw new ConfigurationException("Azure Storage configuration required. " + "Set either '"
                    + CONNECTION_STRING_PROPERTY + "' (for local) " + "or '"
                    + ENDPOINT_PROPERTY + "' (for production) in your configuration.");
        }

        try {
            // Use AKS Workload Identity  only
            WorkloadIdentityCredential credential = new WorkloadIdentityCredentialBuilder()
                    .httpClient(httpClient)
                    .build();
            log.info(
                    "Azure BlobServiceClient initialized for endpoint: {} using AKS Workload Identity (okhttp - connection timeout)",
                    endpoint);
            return new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .credential(credential)
                    .httpClient(httpClient)
                    .buildClient();

        } catch (Exception e) {
            log.error("Failed to initialize Azure Storage Client with Workload Identity", e);
            throw new ConfigurationException("Failed to initialize Azure Storage Client: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a singleton, thread-safe {@link BlobServiceClient}.
     * Priority: Connection string → Service Account authentication.
     *
     * @return configured {@link BlobServiceClient} instance reused across the application
     */
    public static synchronized BlobServiceClient getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }
}
