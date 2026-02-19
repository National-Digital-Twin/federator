package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Factory for a singleton AWS S3 client used across client and server components.
 *
 * Supported configuration via {@link PropertyUtil} keys:
 * - {@code aws.s3.region} – AWS region (optional; falls back to default region provider chain)
 * - {@code aws.s3.access.key.id} – static access key id (optional)
 * - {@code aws.s3.secret.access.key} – static secret access key (optional)
 * - {@code aws.s3.profile} – AWS CLI profile name (optional; supports SSO & non-SSO profiles)
 * - {@code aws.s3.endpoint.url} – optional S3-compatible endpoint (e.g., MinIO)
 * - {@code aws.s3.pathStyle} – true|false to enable path-style addressing (default true)
 *
 * Resolution order:
 * 1) If {@code aws.s3.profile} is set → use {@link ProfileCredentialsProvider} (supports SSO, source_profile/role_arn, etc.).
 * 2) Else if static keys provided → use {@link StaticCredentialsProvider}.
 * 3) Else → use {@link DefaultCredentialsProvider} (EC2/ECS/Lambda IAM role, environment, container creds, etc.).
 *
 * Region resolution:
 * - If {@code aws.s3.region} provided, use it; otherwise resolve with {@link DefaultAwsRegionProviderChain} and finally fall back to {@code us-east-1}.
 *
 * Path-style is enabled by default for broad S3-compatibility (esp. MinIO); can be turned off via {@code aws.s3.pathStyle=false}.
 */
@Slf4j
public final class S3ClientFactory {

    // Lazily initialized singleton to avoid class-load failures if configuration is bad
    private static final AtomicReference<S3Client> s3Client = new AtomicReference<>();

    private S3ClientFactory() {}

    // Orchestrates the modular steps to create the S3 client
    private static S3Client createClient() {
        S3Settings settings = S3Settings.fromProperties();
        return buildClient(settings);
    }

    // Build the S3 client using resolved components
    private static S3Client buildClient(S3Settings settings) {
        AwsCredentialsProvider credentialsProvider = resolveCredentialsProvider(settings);
        Region region = resolveRegion(settings);
        S3Configuration s3Config = buildS3Configuration(settings);

        var builder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Config);

        builder = applyEndpointOverride(builder, settings);

        return builder.build();
    }

    // Select credentials provider based on settings (profile -> static -> default chain)
    private static AwsCredentialsProvider resolveCredentialsProvider(S3Settings settings) {
        if (settings.profile != null && !settings.profile.isBlank()) {
            try {
                log.info("Using AWS profile '{}' for S3 credentials", settings.profile);
                return ProfileCredentialsProvider.create(settings.profile);
            } catch (Exception e) {
                log.warn(
                        "Failed to create ProfileCredentialsProvider for profile '{}'. Falling back to static credentials or default provider chain.",
                        settings.profile,
                        e);
                // fall through to static/default providers
            }
        }
        if (settings.accessKey != null
                && !settings.accessKey.isBlank()
                && settings.secretKey != null
                && !settings.secretKey.isBlank()) {
            log.info("Using static AWS credentials for S3 (access key id provided)");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.accessKey, settings.secretKey));
        }
        log.info("Using DefaultCredentialsProvider for S3 (IAM role / env / container)");
        return DefaultCredentialsProvider.builder().build();
    }

    // Determine region either from explicit configuration or default provider chain
    private static Region resolveRegion(S3Settings settings) {
        if (settings.regionStr != null && !settings.regionStr.isBlank()) {
            return Region.of(settings.regionStr);
        }
        try {
            Region region = DefaultAwsRegionProviderChain.builder().build().getRegion();
            if (region == null) {
                log.warn("Region not found in default chain; defaulting to eu-west-2");
                return Region.EU_WEST_2;
            }
            return region;
        } catch (Exception e) {
            log.warn("Failed to resolve region from default chain; defaulting to eu-west-2", e);
            return Region.EU_WEST_2;
        }
    }

    // Build S3 service configuration
    private static S3Configuration buildS3Configuration(S3Settings settings) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(settings.pathStyle)
                .build();
    }

    // Optionally apply endpoint override, useful for MinIO or custom S3 endpoints
    private static S3ClientBuilder applyEndpointOverride(S3ClientBuilder builder, S3Settings settings) {
        if (settings.endpointUrl != null && !settings.endpointUrl.isBlank()) {
            log.info("Using custom S3 endpoint: {} (path-style: {})", settings.endpointUrl, settings.pathStyle);
            return builder.endpointOverride(URI.create(settings.endpointUrl));
        }
        return builder;
    }

    /** Returns the singleton {@link S3Client} instance configured from properties. */
    public static S3Client getClient() {
        return s3Client.updateAndGet(current -> {
            if (current != null) {
                return current;
            }
            try {
                return createClient();
            } catch (Exception e) {
                log.error("Failed to initialize S3Client from properties.", e);
                throw new IllegalStateException("Failed to initialize S3Client from properties", e);
            }
        });
    }

    /** Resets the singleton instance (primarily for testing). */
    static void resetClient() {
        s3Client.set(null);
    }

    // Encapsulates all properties used to configure the S3 client
    private static final class S3Settings {
        private final String regionStr;
        private final String accessKey;
        private final String secretKey;
        private final String profile;
        private final String endpointUrl;
        private final boolean pathStyle;

        private S3Settings(
                String regionStr,
                String accessKey,
                String secretKey,
                String profile,
                String endpointUrl,
                boolean pathStyle) {
            this.regionStr = regionStr;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            this.profile = profile;
            this.endpointUrl = endpointUrl;
            this.pathStyle = pathStyle;
        }

        static S3Settings fromProperties() {
            // These properties are optional; use null defaults to avoid exceptions when absent
            String regionStr = PropertyUtil.getPropertyValue("aws.s3.region", null);
            String accessKey = PropertyUtil.getPropertyValue("aws.s3.access.key.id", null);
            String secretKey = PropertyUtil.getPropertyValue("aws.s3.secret.access.key", null);
            String profile = PropertyUtil.getPropertyValue("aws.s3.profile", null);
            String endpointUrl = PropertyUtil.getPropertyValue("aws.s3.endpoint.url", "");
            boolean pathStyle = PropertyUtil.getPropertyBooleanValue("aws.s3.pathStyle", "true");
            return new S3Settings(regionStr, accessKey, secretKey, profile, endpointUrl, pathStyle);
        }
    }
}
