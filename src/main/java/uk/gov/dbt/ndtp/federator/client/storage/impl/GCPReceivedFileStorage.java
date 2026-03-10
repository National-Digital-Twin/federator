package uk.gov.dbt.ndtp.federator.client.storage.impl;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.GcsClientFactory;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Stores assembled files to Google Cloud Storage using the shared {@link uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.GcsClientFactory}.
 *
 * <p>Configuration is sourced from properties (shared by client and server):
 * <ul>
 *   <li>{@code files.gcp.bucket} – target bucket (required)</li>
 * </ul>
 * GCP client credentials and endpoint are read by {@code GcsClientFactory} via properties:
 * {@code gcp.storage.project.id}, {@code gcp.storage.credentials.file}, and optional {@code gcp.storage.endpoint.url}.
 */
@Slf4j
public class GCPReceivedFileStorage implements ReceivedFileStorage {

    /**
     * Shared property key for target GCS bucket used by both client and server components.
     */
    private static final String GCP_BUCKET_PROP = "files.gcp.bucket";

    /**
     * Uploads the assembled file to GCS (if bucket is configured) and returns the result.
     *
     * @param localFile absolute path of the assembled file on the local filesystem
     * @param originalFileName original file name from the stream (used to form the GCS object key)
     * @param destination destination or prefix used to build the object key
     * @return {@link StoredFileResult} containing the local path and the GCS URI if upload succeeded
     */
    @Override
    public StoredFileResult store(Path localFile, String originalFileName, String destination) {
        String bucket = resolveBucket();
        if (bucket.isBlank()) {
            log.warn("Storage provider is GCP but bucket is not provided. Skipping upload.");
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        }

        String key = ReceivedFileStorage.super.resolveKey(destination, originalFileName);
        try {
            var uri = upload(localFile, bucket, key);
            if (uri != null) {
                // Success path: we manage local temp cleanup here to satisfy tests
                ReceivedFileStorage.super.deleteLocalTempQuietly(localFile);
                return new StoredFileResult(localFile.toAbsolutePath(), uri);
            }
            // upload() may return null (and may have already attempted deletion). Ensure it's deleted.
            ReceivedFileStorage.super.deleteLocalTempQuietly(localFile);
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        } catch (Exception e) {
            // If an overriding implementation of upload() throws, we must still clean up and return gracefully
            log.error(
                    "Upload threw an exception; deleting temp file {} and returning without remote URI", localFile, e);
            ReceivedFileStorage.super.deleteLocalTempQuietly(localFile);
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        }
    }

    // -------- Helper methods (extracted for testability) --------

    String resolveBucket() {
        String bucket = PropertyUtil.getPropertyValue(GCP_BUCKET_PROP, "");
        return bucket == null ? "" : bucket;
    }

    // Use default key resolution from interface

    String upload(Path localFile, String bucket, String key) {
        try {
            var storage = GcsClientFactory.getClient();
            BlobId blobId = BlobId.of(bucket, key);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            storage.createFrom(blobInfo, localFile);
            String uri = String.format("gs://%s/%s", bucket, key);
            log.info("Uploaded file to GCS at {}", uri);
            return uri;
        } catch (Exception e) {
            log.error(
                    "Failed to upload file to GCS; deleting temp file {} and skipping any Redis updates", localFile, e);
            // On failure, ensure the temporary local file is cleaned up
            ReceivedFileStorage.super.deleteLocalTempQuietly(localFile);
            return null;
        }
    }

    // Use default deletion from interface

    // Use default sanitize/buildKey/normalizeKey from interface
}
