package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import java.io.InputStream;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

/**
 * Fetches a file (blob) from Azure Blob Storage.
 */
public class AzureFileProvider implements FileProvider {

    private final BlobServiceClient blobServiceClient;

    /**
     * Creates an Azure file provider backed by a {@link BlobServiceClient}.
     * The client is expected to be thread-safe and reused by callers.
     *
     * @param blobServiceClient configured Azure {@link BlobServiceClient} used for blob access
     */
    public AzureFileProvider(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    /**
     * Fetches the blob specified in the {@link FileTransferRequest} from Azure Blob Storage.
     *
     * @param request request containing the container and blob path to retrieve
     * @return a {@link FileTransferResult} containing an input stream and the blob size
     * @throws FileFetcherException when the blob cannot be fetched
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

    /**
     * Validates that the Azure blob exists by checking its existence.
     * @param request the file transfer request containing the Azure container and blob path to validate
     * @throws FileTransferException if the Azure blob does not exist or cannot be accessed
     */
    @Override
    public void validatePath(FileTransferRequest request) {
        validateStorageContainer(request, "Azure container");

        executeValidation(
                () -> {
                    BlobClient blobClient = blobServiceClient
                            .getBlobContainerClient(request.storageContainer())
                            .getBlobClient(request.path());

                    if (!blobClient.exists()) {
                        throw new FileTransferException(
                                "Azure blob not found: " + request.storageContainer() + "/" + request.path());
                    }
                },
                "Invalid Azure blob path: " + request.storageContainer() + "/" + request.path());
    }
}
