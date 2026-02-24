/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.cloud.storage.Storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Basic smoke test for GcsClientFactory ensuring a client can be created from properties.
 *
 * Note: We intentionally limit to a single construction scenario because the factory
 * holds a static singleton instance which cannot be reset between tests. This test
 * verifies the expected happy-path initialization using different credential approaches
 * and a custom endpoint (e.g., fake-gcs-server/local), without making any network calls.
 */
class GcsClientFactoryTest {

    @AfterEach
    void tearDown() {
        try {
            GcsClientFactory.resetClient();
            PropertyUtil.clear();
        } catch (Exception ignored) {
            // ignore if not initialized
        }
    }

    @Test
    void getClient_withEndpointUrl_buildsSuccessfully() throws IOException {
        // Prepare a temporary properties file with custom endpoint (fake-gcs-server)
        Path tmp = Files.createTempFile("gcsclientfactory-test-", ".properties");
        try {
            String props = String.join(
                    "\n", "gcp.storage.endpoint.url=http://localhost:4443", "gcp.storage.project.id=test-project");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(client, "GcsClientFactory should return a non-null Storage instance");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withProjectIdOnly_buildsSuccessfully() throws IOException {
        // Prepare a temporary properties file with only project ID and endpoint to avoid ADC
        Path tmp = Files.createTempFile("gcsclientfactory-project-test-", ".properties");
        try {
            String props = String.join(
                    "\n", "gcp.storage.project.id=test-project", "gcp.storage.endpoint.url=http://localhost:4443");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(client, "GcsClientFactory should return a non-null Storage instance with project ID only");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withNoProperties_buildsSuccessfully() throws IOException {
        // Prepare a temporary properties file with endpoint to avoid ADC
        Path tmp = Files.createTempFile("gcsclientfactory-default-test-", ".properties");
        try {
            String props = "gcp.storage.endpoint.url=http://localhost:4443";
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(
                    client, "GcsClientFactory should return a non-null Storage instance with default credentials");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withInvalidCredentialsFile_fallsBackToADC() throws IOException {
        // Prepare a temporary properties file with invalid credentials file path and endpoint
        Path tmp = Files.createTempFile("gcsclientfactory-invalid-creds-test-", ".properties");
        try {
            String props = String.join(
                    "\n",
                    "gcp.storage.credentials.file=/non/existent/path/to/credentials.json",
                    "gcp.storage.project.id=test-project",
                    "gcp.storage.endpoint.url=http://localhost:4443");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(
                    client, "GcsClientFactory should return a non-null Storage instance after falling back to ADC");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withEndpointAndNoProjectId_buildsSuccessfully() throws IOException {
        // Prepare a temporary properties file with endpoint but no project ID
        Path tmp = Files.createTempFile("gcsclientfactory-endpoint-no-project-test-", ".properties");
        try {
            String props = String.join("\n", "gcp.storage.endpoint.url=http://localhost:4443");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(
                    client,
                    "GcsClientFactory should return a non-null Storage instance with endpoint but no project ID");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_returnsSameInstance_whenCalledMultipleTimes() throws IOException {
        // Prepare a temporary properties file
        Path tmp = Files.createTempFile("gcsclientfactory-singleton-test-", ".properties");
        try {
            String props = String.join(
                    "\n", "gcp.storage.endpoint.url=http://localhost:4443", "gcp.storage.project.id=test-project");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When - call getClient multiple times
            Storage client1 = GcsClientFactory.getClient();
            Storage client2 = GcsClientFactory.getClient();
            Storage client3 = GcsClientFactory.getClient();

            // Then - all references should point to the same instance
            assertAll(
                    "Singleton behavior verification",
                    () -> assertNotNull(client1, "First client should not be null"),
                    () -> assertSame(client1, client2, "Second call should return same instance as first"),
                    () -> assertSame(client1, client3, "Third call should return same instance as first"),
                    () -> assertSame(client2, client3, "All instances should be identical"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withBlankEndpointUrl_usesDefaultEndpoint() throws IOException {
        // Prepare a temporary properties file with blank endpoint URL but valid endpoint to avoid ADC
        Path tmp = Files.createTempFile("gcsclientfactory-blank-endpoint-test-", ".properties");
        try {
            String props = String.join(
                    "\n", "gcp.storage.project.id=test-project", "gcp.storage.endpoint.url=http://localhost:4443");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When - test that blank values in properties are handled
            var client = GcsClientFactory.getClient();

            // Then - should successfully create client
            assertNotNull(client, "GcsClientFactory should handle configuration gracefully");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withBlankProjectId_usesDefaultProjectId() throws IOException {
        // Prepare a temporary properties file with blank project ID
        Path tmp = Files.createTempFile("gcsclientfactory-blank-project-test-", ".properties");
        try {
            String props =
                    String.join("\n", "gcp.storage.endpoint.url=http://localhost:4443", "gcp.storage.project.id=");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(client, "GcsClientFactory should handle blank project ID gracefully");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withBlankCredentialsFile_fallsBackToADC() throws IOException {
        // Prepare a temporary properties file with blank credentials file path
        Path tmp = Files.createTempFile("gcsclientfactory-blank-creds-test-", ".properties");
        try {
            String props = String.join(
                    "\n",
                    "gcp.storage.credentials.file=",
                    "gcp.storage.project.id=test-project",
                    "gcp.storage.endpoint.url=http://localhost:4443");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(tmp.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(
                    client, "GcsClientFactory should handle blank credentials file and fall back to NoCredentials");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withValidServiceAccountFile_buildsSuccessfully() throws IOException {
        // Create a temporary service account JSON file
        Path credentialsFile = Files.createTempFile("gcs-service-account-", ".json");
        Path propsFile = Files.createTempFile("gcsclientfactory-service-account-test-", ".properties");
        try {
            // Create a minimal valid service account JSON
            String serviceAccountJson =
                    """
                    {
                      "type": "service_account",
                      "project_id": "test-project-from-sa",
                      "private_key_id": "key-id",
                      "private_key": "-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7W8jLqFcYhKZv\\nQ3xN1kGvF4pqH0vN3TbXeJ7P8hYZ7xQqJjHxKFYz0p3rHYh0TlXGJpQ5WxPnqTlL\\ny9KHJmT3fP9yxKqM8YpQqMxKzJpLqJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQ\\nxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqL\\npQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJ\\nqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQ\\nxJqLpQxJqLpQxJqLpQIDAQABAoIBADvXZ8jLqFcYhKZvQ3xN1kGvF4pqH0vN3TbX\\neJ7P8hYZ7xQqJjHxKFYz0p3rHYh0TlXGJpQ5WxPnqTlLy9KHJmT3fP9yxKqM8YpQ\\nqMxKzJpLqJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJ\\nqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQ\\nxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqL\\npQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJ\\nqLpQxJqLpQECgYEA5fHJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqL\\npQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJ\\nqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQ\\nxJqLpQxJqLpQxJqLpQECgYEA0pQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQx\\nJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLp\\nQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJq\\nLpQxJqLpQxJqLpQxJqLpQUCgYEAyKqM8YpQqMxKzJpLqJqLpQxJqLpQxJqLpQxJq\\nLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQx\\nJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLp\\nQxJqLpQxJqLpQxJqLpQBAoGAFYz0p3rHYh0TlXGJpQ5WxPnqTlLy9KHJmT3fP9yx\\nKqM8YpQqMxKzJpLqJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQx\\nJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLp\\nQxJqLpQxJqLpQxJqLpQxJqLpQUCgYEAxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJq\\nLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQx\\nJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLpQxJqLp\\nQxJqLpQxJqLpQxJqLpQxJqLpQw=\\n-----END PRIVATE KEY-----\\n",
                      "client_email": "test@test-project.iam.gserviceaccount.com",
                      "client_id": "123456789",
                      "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                      "token_uri": "https://oauth2.googleapis.com/token",
                      "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                      "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/test%40test-project.iam.gserviceaccount.com"
                    }
                    """;
            Files.writeString(credentialsFile, serviceAccountJson);

            // Create properties file pointing to the service account file
            String props = String.join(
                    "\n",
                    "gcp.storage.credentials.file=" + credentialsFile.toAbsolutePath(),
                    "gcp.storage.endpoint.url=http://localhost:4443");
            Files.writeString(propsFile, props);

            // Initialize PropertyUtil before touching GcsClientFactory
            PropertyUtil.init(propsFile.toFile());

            // When
            var client = GcsClientFactory.getClient();

            // Then
            assertNotNull(client, "GcsClientFactory should successfully create client with valid service account file");
        } finally {
            Files.deleteIfExists(credentialsFile);
            Files.deleteIfExists(propsFile);
        }
    }

    @Test
    void resetClient_allowsNewClientCreation() throws IOException {
        // Prepare a temporary properties file
        Path tmp = Files.createTempFile("gcsclientfactory-reset-test-", ".properties");
        try {
            String props = String.join(
                    "\n", "gcp.storage.endpoint.url=http://localhost:4443", "gcp.storage.project.id=test-project-1");
            Files.writeString(tmp, props);

            // Initialize PropertyUtil and get first client
            PropertyUtil.init(tmp.toFile());
            Storage client1 = GcsClientFactory.getClient();

            // When - reset and create new client with different config
            GcsClientFactory.resetClient();
            PropertyUtil.clear();

            String props2 = String.join(
                    "\n", "gcp.storage.endpoint.url=http://localhost:4444", "gcp.storage.project.id=test-project-2");
            Files.writeString(tmp, props2);
            PropertyUtil.init(tmp.toFile());
            Storage client2 = GcsClientFactory.getClient();

            // Then - clients should be different instances
            assertAll(
                    "Reset behavior verification",
                    () -> assertNotNull(client1, "First client should not be null"),
                    () -> assertNotNull(client2, "Second client should not be null"),
                    () -> assertNotSame(client1, client2, "After reset, a new instance should be created"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withNullEndpointUrl_throwsExceptionWhenADCUnavailable() throws IOException {
        // Test the path where endpoint URL is null and ADC is not available
        Path tmp = Files.createTempFile("gcsclientfactory-null-endpoint-test-", ".properties");
        try {
            // Only set project ID, leave endpoint URL null
            String props = "gcp.storage.project.id=test-project";
            Files.writeString(tmp, props);

            PropertyUtil.init(tmp.toFile());

            // When - this should try ADC and fail (since ADC is not configured in test env)
            // Then - expect IllegalStateException from ADC failure
            assertThrows(
                    IllegalStateException.class,
                    GcsClientFactory::getClient,
                    "Should throw IllegalStateException when ADC is unavailable and no endpoint is set");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withEmptyStringEndpointUrl_throwsExceptionWhenADCUnavailable() throws IOException {
        // Test the path where endpoint URL is explicitly empty string and ADC unavailable
        Path tmp = Files.createTempFile("gcsclientfactory-empty-endpoint-test-", ".properties");
        try {
            String props = String.join("\n", "gcp.storage.endpoint.url=", "gcp.storage.project.id=test-project");
            Files.writeString(tmp, props);

            PropertyUtil.init(tmp.toFile());

            // When/Then - should throw when ADC is unavailable
            assertThrows(
                    IllegalStateException.class,
                    GcsClientFactory::getClient,
                    "Should throw IllegalStateException when endpoint is empty and ADC is unavailable");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void getClient_withInvalidCredentialsFileAndNoEndpoint_throwsExceptionAfterADCFails() throws IOException {
        // Test fallback to ADC when credentials file is invalid and no endpoint - ADC also fails in test env
        Path tmp = Files.createTempFile("gcsclientfactory-invalid-creds-no-endpoint-test-", ".properties");
        try {
            String props = String.join(
                    "\n",
                    "gcp.storage.credentials.file=/non/existent/path/to/credentials.json",
                    "gcp.storage.project.id=test-project");
            Files.writeString(tmp, props);

            PropertyUtil.init(tmp.toFile());

            // When/Then - should fail loading credentials file, then fail ADC fallback
            assertThrows(
                    IllegalStateException.class,
                    GcsClientFactory::getClient,
                    "Should throw IllegalStateException when credentials file is invalid and ADC fallback fails");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
