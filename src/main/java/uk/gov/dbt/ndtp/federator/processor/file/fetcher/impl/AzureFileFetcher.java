package uk.gov.dbt.ndtp.federator.processor.file.fetcher.impl;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import java.io.InputStream;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.model.FileFetchRequest;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetchResult;
import uk.gov.dbt.ndtp.federator.processor.file.fetcher.FileFetcher;

/**
 * Fetches a file (blob) from Azure Blob Storage.
 */
public class AzureFileFetcher implements FileFetcher {

    private final BlobServiceClient blobServiceClient;

    public AzureFileFetcher(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    @Override
    public FileFetchResult fetch(FileFetchRequest request) {
        try {
            BlobClient blobClient = blobServiceClient
                    .getBlobContainerClient(request.bucketOrContainer())
                    .getBlobClient(request.path());

            BlobProperties props = blobClient.getProperties();
            long fileSize = props.getBlobSize();

            InputStream stream = blobClient.openInputStream();

            return new FileFetchResult(stream, fileSize);

        } catch (Exception e) {
            throw new FileFetcherException(
                    "Failed to fetch blob from Azure: " + request.bucketOrContainer() + "/" + request.path(), e);
        }
    }
}
