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
 * <p>Configuration is sourced from {@code client.properties}:
 * <ul>
 *   <li>{@code client.files.s3.bucket} – target bucket (required)</li>
 *   <li>{@code client.files.s3.keyPrefix} – optional key prefix for objects</li>
 * </ul>
 * AWS client credentials and endpoint are read by {@code S3ClientFactory} via properties:
 * {@code aws.s3.region}, {@code aws.s3.access.key.id}, {@code aws.s3.secret.access.key}, and optional {@code aws.s3.endpoint.url}.
 */
@Slf4j
public class S3ReceivedFileStorage implements ReceivedFileStorage {

    private static final String S3_BUCKET_PROP = "client.files.s3.bucket";
    private static final String S3_KEY_PREFIX_PROP = "client.files.s3.keyPrefix";

    /**
     * Uploads the assembled file to S3 (if bucket is configured) and returns the result.
     *
     * @param localFile absolute path of the assembled file on the local filesystem
     * @param originalFileName original file name from the stream (used to form the S3 key)
     * @return {@link StoredFileResult} containing the local path and the S3 URI if upload succeeded
     */
    @Override
    public StoredFileResult store(Path localFile, String originalFileName) {
        String bucket = PropertyUtil.getPropertyValue(S3_BUCKET_PROP, "");
        if (bucket == null || bucket.isBlank()) {
            log.warn("Storage provider is S3 but '{}' is not configured; skipping upload.", S3_BUCKET_PROP);
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        }
        String key = buildS3Key(PropertyUtil.getPropertyValue(S3_KEY_PREFIX_PROP, ""), sanitize(originalFileName));
        try {
            var s3 = S3ClientFactory.getClient();
            var putReq = PutObjectRequest.builder().bucket(bucket).key(key).build();
            s3.putObject(putReq, RequestBody.fromFile(localFile));
            String uri = String.format("s3://%s/%s", bucket, key);
            log.info("Uploaded file to S3 at {}", uri);
            return new StoredFileResult(localFile.toAbsolutePath(), uri);
        } catch (Exception e) {
            log.error("Failed to upload file to S3; file remains locally at {}", localFile, e);
            return new StoredFileResult(localFile.toAbsolutePath(), null);
        }
    }

    private String sanitize(String name) {
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private String buildS3Key(String prefix, String fileName) {
        String p = prefix == null ? "" : prefix.trim();
        if (p.startsWith("/")) p = p.substring(1);
        if (!p.isEmpty() && !p.endsWith("/")) p = p + "/";
        return p + fileName;
    }
}
