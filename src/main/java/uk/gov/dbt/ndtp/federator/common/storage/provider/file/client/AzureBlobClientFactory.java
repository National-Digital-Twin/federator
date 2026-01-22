package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import com.azure.identity.WorkloadIdentityCredential;
import com.azure.identity.WorkloadIdentityCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
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
        try {
            connectionString = PropertyUtil.getPropertyValue(CONNECTION_STRING_PROPERTY);
        } catch (Exception e) {
            log.debug("Connection string property not found");
        }

        if (connectionString != null && !connectionString.isBlank()) {
            log.info("Azure Storage Client initialized with connection string (local/emulator mode)");
            return new BlobServiceClientBuilder()
                    .connectionString(connectionString)
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
            // Use System-Assigned Workload Identity only
            WorkloadIdentityCredential credential = new WorkloadIdentityCredentialBuilder().build();
            logInfoProperties();
            log.info("Azure BlobServiceClient initialized for endpoint: {} using System Workload Identity", endpoint);
            return new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .credential(credential)
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

    private static void logInfoProperties() {
        String clientIdSet = System.getenv("AZURE_CLIENT_ID");
        String tenantIdSet = System.getenv("AZURE_TENANT_ID");
        String tokenFile = System.getenv("AZURE_FEDERATED_TOKEN_FILE");

        log.info(
                "Init Azure Storage client using Workload Identity. "
                        + "AZURE_CLIENT_ID={}, AZURE_TENANT_ID={}, AZURE_FEDERATED_TOKEN_FILE={}",
                clientIdSet,
                tenantIdSet,
                tokenFile);
    }
}
