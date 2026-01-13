package uk.gov.dbt.ndtp.federator.client.storage.impl;

import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.S3ClientFactory;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Stores assembled files to Amazon S3 using the shared {@link uk.gov.dbt.ndtp.federator.common.storage.provider.file.client.S3ClientFactory}.
 *
 * <p>Configuration is sourced from properties (shared by client and server):
 * <ul>
 *   <li>{@code files.s3.bucket} – target bucket (required)</li>
 * </ul>
 * AWS client credentials and endpoint are read by {@code S3ClientFactory} via properties:
 * {@code aws.s3.region}, {@code aws.s3.access.key.id}, {@code aws.s3.secret.access.key}, and optional {@code aws.s3.endpoint.url}.
 */
@Slf4j
public class S3ReceivedFileStorage implements ReceivedFileStorage {

    /**
     * Shared property key for target S3 bucket used by both client and server components.
     */
    private static final String S3_BUCKET_PROP = "files.s3.bucket";

    /**
     * Uploads the assembled file to S3 (if bucket is configured) and returns the result.
     *
     * @param localFile absolute path of the assembled file on the local filesystem
     * @param originalFileName original file name from the stream (used to form the S3 key)
     * @return {@link StoredFileResult} containing the local path and the S3 URI if upload succeeded
     */
    @Override
    public StoredFileResult store(Path localFile, String originalFileName, String destination) {
        String bucket = resolveBucket();
        if (bucket.isBlank()) {
            log.warn("Storage provider is S3 but bucket is not provided. Skipping upload.");
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
        String bucket = PropertyUtil.getPropertyValue(S3_BUCKET_PROP, "");
        return bucket == null ? "" : bucket;
    }

    // Use default key resolution from interface

    String upload(Path localFile, String bucket, String key) {
        try {
            var s3 = S3ClientFactory.getClient();
            var putReq = PutObjectRequest.builder().bucket(bucket).key(key).build();
            s3.putObject(putReq, RequestBody.fromFile(localFile));
            String uri = String.format("s3://%s/%s", bucket, key);
            log.info("Uploaded file to S3 at {}", uri);
            return uri;
        } catch (Exception e) {
            log.error(
                    "Failed to upload file to S3; deleting temp file {} and skipping any Redis updates", localFile, e);
            // On failure, ensure the temporary local file is cleaned up
            ReceivedFileStorage.super.deleteLocalTempQuietly(localFile);
            return null;
        }
    }

    // Use default deletion from interface

    // Use default sanitize/buildKey/normalizeKey from interface
}
