/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
