package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Basic smoke test for S3ClientFactory ensuring a client can be created from properties.
 *
 * Note: We intentionally limit to a single construction scenario because the factory
 * holds a static singleton instance which cannot be reset between tests. This test
 * verifies the expected happy-path initialization using static credentials and a
 * custom endpoint (e.g., MinIO/local), without making any network calls.
 */
class S3ClientFactoryTest {

    @AfterEach
    void tearDown() {
        try {
            S3ClientFactory.resetClient();
            PropertyUtil.clear();
        } catch (Exception ignored) {
            // ignore if not initialized
        }
    }

    @Test
    void getClient_withStaticKeysAndEndpoint_buildsSuccessfully() throws IOException {
        // Prepare a temporary properties file with minimal configuration
        Path tmp = Files.createTempFile("s3clientfactory-test-", ".properties");
        try {
            String props = String.join(
                    "\n",
                    "aws.s3.access.key.id=minioadmin",
                    "aws.s3.secret.access.key=minioadmin",
                    "aws.s3.endpoint.url=http://localhost:9000",
                    // Region is required by SDK even for custom endpoints
                    "aws.s3.region=us-east-1",
                    // Path style true for broad S3-compatibility
                    "aws.s3.pathStyle=true");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching S3ClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = S3ClientFactory.getClient();

            // Then
            assertNotNull(client, "S3ClientFactory should return a non-null S3Client instance");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withDefaultCredentials_buildsSuccessfully() throws IOException {
        // Prepare a temporary properties file with NO credentials
        Path tmp = Files.createTempFile("s3clientfactory-default-test-", ".properties");
        try {
            String props = String.join(
                    "\n",
                    "aws.s3.region=us-east-1",
                    "aws.s3.pathStyle=true");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching S3ClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            // Since we can't reset the singleton in S3ClientFactory easily without reflection,
            // and the previous test might have already initialized it, we might be getting
            // the same instance. However, if this is the first time it's called with these properties,
            // it would try to build. But wait, S3ClientFactory has a static singleton.

            // If the singleton is already set, getClient() just returns it.
            var client = S3ClientFactory.getClient();

            // Then
            assertNotNull(client, "S3ClientFactory should return a non-null S3Client instance");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withProfile_buildsSuccessfully() throws IOException {
        // Prepare a temporary properties file with a profile name
        Path tmp = Files.createTempFile("s3clientfactory-profile-test-", ".properties");
        try {
            String props = String.join(
                    "\n",
                    "aws.s3.profile=test-profile",
                    "aws.s3.region=us-east-1");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching S3ClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = S3ClientFactory.getClient();

            // Then
            assertNotNull(client, "S3ClientFactory should return a non-null S3Client instance even if profile doesn't exist (it falls back)");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
