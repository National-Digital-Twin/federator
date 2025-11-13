package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ConfigurationException;

/**
 * Factory for a singleton AWS S3 client used across client and server components.
 *
 * <p>Reads configuration via {@link uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil}:
 * <ul>
 *   <li>{@code aws.s3.region} – AWS region (default {@code us-east-1})</li>
 *   <li>{@code aws.s3.access.key.id} – access key id (required)</li>
 *   <li>{@code aws.s3.secret.access.key} – secret access key (required)</li>
 *   <li>{@code aws.s3.endpoint.url} – optional S3-compatible endpoint (e.g., MinIO)</li>
 * </ul>
 * Uses path-style access for compatibility with S3-compatible providers.
 */
@Slf4j
public final class S3ClientFactory {

    private static final S3Client CLIENT = createClient();

    private S3ClientFactory() {}

    private static S3Client createClient() {
        // Read properties
        String regionStr = PropertyUtil.getPropertyValue("aws.s3.region", "us-east-1");
        String accessKey = PropertyUtil.getPropertyValue("aws.s3.access.key.id");
        String secretKey = PropertyUtil.getPropertyValue("aws.s3.secret.access.key");
        String endpointUrl = PropertyUtil.getPropertyValue("aws.s3.endpoint.url", "");

        if (accessKey == null || secretKey == null) {
            log.error("Set both 'aws.s3.access.key.id' and 'aws.s3.secret.access.key' properties");
            throw new ConfigurationException("AWS S3 credentials are required. "
                    + "Set both 'aws.s3.access.key.id' and 'aws.s3.secret.access.key' properties.");
        }

        var creds = AwsBasicCredentials.create(accessKey, secretKey);
        var builder = S3Client.builder()
                .region(Region.of(regionStr))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                // Enable path-style to support S3-compatible endpoints like MinIO
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build());

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpointUrl));
        }

        return builder.build();
    }

    /**
     * Returns the singleton {@link S3Client} instance configured from properties.
     *
     * @return configured AWS S3 client
     */
    public static S3Client getClient() {
        return CLIENT;
    }
}
