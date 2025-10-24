package uk.gov.dbt.ndtp.federator.processor.file.fetcher.client;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import uk.gov.dbt.ndtp.federator.exceptions.ConfigurationException;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

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

    public static S3Client getClient() {
        return CLIENT;
    }
}
