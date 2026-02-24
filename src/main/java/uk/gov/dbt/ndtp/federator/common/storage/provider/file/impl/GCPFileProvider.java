package uk.gov.dbt.ndtp.federator.common.storage.provider.file.impl;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.InputStream;
import java.nio.channels.Channels;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.exceptions.FileFetcherException;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileTransferResult;

/**
 * {@link FileProvider} implementation that fetches files from Google Cloud Storage using an injected {@link com.google.cloud.storage.Storage}.
 * Resolves object size via a metadata call before opening the GET stream.
 */
public class GCPFileProvider implements FileProvider {

    private final Storage storage;

    public GCPFileProvider(Storage storage) {
        this.storage = storage;
    }

    /**
     * Fetches the file specified in the FileTransferRequest from GCS.
     * @param request
     * @return
     */
    @Override
    public FileTransferResult get(FileTransferRequest request) {
        try {
            BlobId blobId = BlobId.of(request.storageContainer(), request.path());

            Blob blob = storage.get(blobId);
            if (blob == null || !blob.exists()) {
                throw new FileFetcherException(
                        "File not found in GCS: " + request.storageContainer() + "/" + request.path());
            }

            long size = blob.getSize();
            InputStream stream = Channels.newInputStream(blob.reader());

            return new FileTransferResult(stream, size);

        } catch (FileFetcherException e) {
            throw e;
        } catch (StorageException e) {
            if (e.getCode() == 404) {
                throw new FileFetcherException(
                        "File not found in GCS: " + request.storageContainer() + "/" + request.path());
            }
            throw new FileFetcherException(
                    "GCS error fetching: " + request.storageContainer() + "/" + request.path(), e);
        } catch (Exception e) {
            throw new FileFetcherException(
                    "Failed to fetch from GCS: " + request.storageContainer() + "/" + request.path(), e);
        }
    }

    /**
     * Validates that the GCS object exists by checking its metadata.
     * @param request the file transfer request containing the GCS bucket and object path to validate
     * @throws FileTransferException if the GCS object does not exist or cannot be accessed
     */
    @Override
    public void validatePath(FileTransferRequest request) {
        validateStorageContainer(request, "GCS bucket");

        executeValidation(
                () -> {
                    try {
                        BlobId blobId = BlobId.of(request.storageContainer(), request.path());
                        Blob blob = storage.get(blobId);
                        if (blob == null || !blob.exists()) {
                            throw new FileTransferException(
                                    "GCS object not found: " + request.storageContainer() + "/" + request.path());
                        }
                    } catch (StorageException e) {
                        if (e.getCode() == 404) {
                            throw new FileTransferException(
                                    "GCS object not found: " + request.storageContainer() + "/" + request.path());
                        }
                        throw new FileTransferException(
                                "GCS validation error: " + request.storageContainer() + "/" + request.path(), e);
                    }
                },
                "Invalid GCS path: " + request.storageContainer() + "/" + request.path());
    }
}
