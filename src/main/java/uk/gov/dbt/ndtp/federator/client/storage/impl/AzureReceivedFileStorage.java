package uk.gov.dbt.ndtp.federator.client.storage.impl;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.AzureBlobClientFactory;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Stores assembled files to Azure Blob Storage using the shared {@link
 * uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.AzureBlobClientFactory}.
 *
 * <p>Configuration is sourced from properties (shared by client and server):
 * <ul>
 *   <li>{@code files.azure.container} – target container (required)</li>
 * </ul>
 * Azure client connection is handled by {@code AzureBlobClientFactory} via the
 * {@code azure.storage.connection.string} property.</p>
 */
@Slf4j
public class AzureReceivedFileStorage implements ReceivedFileStorage {

    private static final String AZURE_CONTAINER_PROP = "files.azure.container";

    /**
     * Uploads the assembled file to Azure Blob Storage (if container is configured) and returns the result.
     *
     * @param localFile absolute path of the assembled file on the local filesystem
     * @param originalFileName original file name from the stream (used to form the blob path)
     * @param destination destination or prefix used to build the blob path
     * @return {@link StoredFileResult} containing the local path and the Azure URI if upload succeeded
     */
    @Override
    public StoredFileResult store(Path localFile, String originalFileName, String destination) {
        String container = resolveContainer();
        if (container.isBlank()) {
            log.warn("Storage provider is AZURE but container is not provided. Skipping upload.");
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        }

        String blobPath = resolveKey(destination, originalFileName);
        try {
            String uri = upload(localFile, container, blobPath);
            if (uri != null) {
                deleteLocalTempQuietly(localFile);
                return new StoredFileResult(localFile.toAbsolutePath(), uri);
            }
            deleteLocalTempQuietly(localFile);
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        } catch (Exception e) {
            log.error(
                    "Azure upload threw an exception; deleting temp file {} and returning without remote URI",
                    localFile,
                    e);
            deleteLocalTempQuietly(localFile);
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        }
    }

    String resolveContainer() {
        String container = PropertyUtil.getPropertyValue(AZURE_CONTAINER_PROP, "");
        return container == null ? "" : container;
    }

    String upload(Path localFile, String container, String blobPath) {
        try {
            BlobServiceClient client = AzureBlobClientFactory.getClient();

            BlobContainerClient containerClient = client.getBlobContainerClient(container);

            log.info("Canary: checking container exists...");
            boolean containerExists = containerClient.exists();
            log.info("Canary: container exists={}", containerExists);

            BlobClient blobClient = containerClient.getBlobClient(blobPath);
            log.info("Canary: checking blob exists...");
            boolean blobExists = blobClient.exists();
            log.info("Canary: blob exists={}", blobExists);
            blobClient.uploadFromFile(localFile.toString(), true);
            String uri = String.format("azure://%s/%s", container, blobPath);
            log.info("Uploaded file to Azure Blob at {}", uri);
            return uri;
        } catch (Exception e) {
            log.error(
                    "Failed to upload file to Azure; deleting temp file {} and skipping any Redis updates",
                    localFile,
                    e);
            deleteLocalTempQuietly(localFile);
            return null;
        }
    }
}
