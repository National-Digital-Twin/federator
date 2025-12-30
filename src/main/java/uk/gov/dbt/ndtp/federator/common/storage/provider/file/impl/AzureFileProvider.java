package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import java.io.InputStream;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

/**
 * Fetches a file (blob) from Azure Blob Storage.
 */
public class AzureFileProvider implements FileProvider {

    private final BlobServiceClient blobServiceClient;

    public AzureFileProvider(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    /**
     * Fetches the blob specified in the FileTransferRequest from Azure Blob Storage.
     * @param request
     * @return
     */
    @Override
    public FileTransferResult get(FileTransferRequest request) {
        try {
            BlobClient blobClient = blobServiceClient
                    .getBlobContainerClient(request.storageContainer())
                    .getBlobClient(request.path());

            BlobProperties props = blobClient.getProperties();
            long fileSize = props.getBlobSize();

            InputStream stream = blobClient.openInputStream();

            return new FileTransferResult(stream, fileSize);

        } catch (Exception e) {
            throw new FileFetcherException(
                    "Failed to fetch blob from Azure: " + request.storageContainer() + "/" + request.path(), e);
        }
    }
}
