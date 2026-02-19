package uk.gov.dbt.ndtp.federator.common.storage.provider.file.client;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Factory for a singleton Google Cloud Storage client used across client and server components.
 *
 * Supported configuration via {@link PropertyUtil} keys:
 * - {@code gcp.storage.project.id} – GCP project ID (optional; falls back to default)
 * - {@code gcp.storage.credentials.file} – path to service account JSON key file (optional)
 * - {@code gcp.storage.endpoint.url} – optional GCS-compatible endpoint (e.g., fake-gcs-server)
 *
 * Resolution order:
 * 1) If {@code gcp.storage.credentials.file} is set → use {@link ServiceAccountCredentials} from file.
 * 2) Else → use {@link GoogleCredentials#getApplicationDefault()} (ADC: env var, gcloud, GCE/GKE metadata, etc.).
 *
 * Project ID resolution:
 * - If {@code gcp.storage.project.id} provided, use it; otherwise fall back to default from credentials or environment.
 *
 * Custom endpoint support for local testing (e.g., fake-gcs-server).
 */
@Slf4j
public final class GcsClientFactory {

    // Lazily initialized singleton to avoid class-load failures if configuration is bad
    private static final AtomicReference<Storage> gcsClient = new AtomicReference<>();

    private GcsClientFactory() {}

    // Orchestrates the modular steps to create the GCS client
    private static Storage createClient() {
        GcsSettings settings = GcsSettings.fromProperties();
        return buildClient(settings);
    }

    // Build the GCS client using resolved components
    private static Storage buildClient(GcsSettings settings) {
        Credentials credentials = resolveCredentials(settings);
        String projectId = resolveProjectId(settings, credentials);

        StorageOptions.Builder builder = StorageOptions.newBuilder().setCredentials(credentials);

        if (projectId != null && !projectId.isBlank()) {
            builder = builder.setProjectId(projectId);
        }

        builder = applyEndpointOverride(builder, settings);

        return builder.build().getService();
    }

    // Select credentials based on settings (service account file -> application default)
    private static Credentials resolveCredentials(GcsSettings settings) {
        if (settings.endpointUrl != null && !settings.endpointUrl.isBlank()) {
            log.info("GCS emulator endpoint configured; using NoCredentials");
            return NoCredentials.getInstance();
        }

        if (settings.credentialsFile != null && !settings.credentialsFile.isBlank()) {
            try (FileInputStream in = new FileInputStream(settings.credentialsFile)) {
                log.info("Using GCP service account credentials from file: {}", settings.credentialsFile);
                return ServiceAccountCredentials.fromStream(in);
            } catch (IOException e) {
                log.warn(
                        "Failed to load service account credentials from '{}'. Falling back to ADC.",
                        settings.credentialsFile,
                        e);
            }
        }

        try {
            log.info("Using Application Default Credentials for GCS");
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to obtain Application Default Credentials for GCS", e);
        }
    }

    // Determine project ID from explicit configuration or credentials
    private static String resolveProjectId(GcsSettings settings, Credentials credentials) {
        if (settings.projectId != null && !settings.projectId.isBlank()) {
            return settings.projectId;
        }
        if (credentials instanceof ServiceAccountCredentials) {

            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            if (projectId != null) {
                log.info("Resolved GCP project ID from service account credentials: {}", projectId);
                return projectId;
            }
        }
        log.info("No explicit GCP project ID configured; will use default from environment");
        return null;
    }

    // Optionally apply endpoint override, useful for fake-gcs-server or custom GCS endpoints
    private static StorageOptions.Builder applyEndpointOverride(StorageOptions.Builder builder, GcsSettings settings) {
        if (settings.endpointUrl != null && !settings.endpointUrl.isBlank()) {
            log.info("Using custom GCS endpoint: {}", settings.endpointUrl);
            return builder.setHost(settings.endpointUrl);
        }
        return builder;
    }

    /** Returns the singleton {@link Storage} instance configured from properties. */
    public static Storage getClient() {
        return gcsClient.updateAndGet(current -> {
            if (current != null) {
                return current;
            }
            try {
                return createClient();
            } catch (Exception e) {
                log.error("Failed to initialize GCS Storage client from properties.", e);
                throw new IllegalStateException("Failed to initialize GCS Storage client from properties", e);
            }
        });
    }

    /** Resets the singleton instance (primarily for testing). */
    static void resetClient() {
        gcsClient.set(null);
    }

    // Encapsulates all properties used to configure the GCS client
    private static final class GcsSettings {
        private final String projectId;
        private final String credentialsFile;
        private final String endpointUrl;

        private GcsSettings(String projectId, String credentialsFile, String endpointUrl) {
            this.projectId = projectId;
            this.credentialsFile = credentialsFile;
            this.endpointUrl = endpointUrl;
        }

        static GcsSettings fromProperties() {
            // These properties are optional; use null defaults to avoid exceptions when absent
            String projectId = PropertyUtil.getPropertyValue("gcp.storage.project.id", null);
            String credentialsFile = PropertyUtil.getPropertyValue("gcp.storage.credentials.file", null);
            String endpointUrl = PropertyUtil.getPropertyValue("gcp.storage.endpoint.url", "");
            return new GcsSettings(projectId, credentialsFile, endpointUrl);
        }
    }
}
