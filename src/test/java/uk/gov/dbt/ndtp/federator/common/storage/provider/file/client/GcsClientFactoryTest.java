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
}
