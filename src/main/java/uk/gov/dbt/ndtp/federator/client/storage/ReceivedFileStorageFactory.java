package uk.gov.dbt.ndtp.federator.client.storage;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.storage.impl.AzureReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.impl.LocalReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.impl.S3ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Factory for selecting a {@link uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorage}
 * implementation based on {@code client.files.storage.provider} (LOCAL | S3 | AZURE).
 *
 * <p>Defaults to LOCAL when the property is missing or has an unknown value.</p>
 */
@Slf4j
public final class ReceivedFileStorageFactory {

    private static final String STORAGE_PROVIDER_PROP = "client.files.storage.provider"; // LOCAL | S3 | AZURE

    private ReceivedFileStorageFactory() {}

    /**
     * Returns a storage implementation based on {@code client.files.storage.provider}.
     *
     * <p>Recognized values (case-insensitive):
     * <ul>
     *   <li>{@code S3} – returns {@link S3ReceivedFileStorage}</li>
     *   <li>{@code AZURE} – returns {@link AzureReceivedFileStorage}</li>
     *   <li>{@code LOCAL} or any other value – returns {@link LocalReceivedFileStorage}</li>
     * </ul>
     *
     * @return a concrete {@link ReceivedFileStorage} implementation according to the property
     */
    public static ReceivedFileStorage get() {
        String provider;
        try {
            provider = PropertyUtil.getPropertyValue(STORAGE_PROVIDER_PROP, "LOCAL");
        } catch (RuntimeException ex) {
            log.debug("Property '{}' not available; defaulting to LOCAL", STORAGE_PROVIDER_PROP, ex);
            provider = "LOCAL";
        }

        if ("S3".equalsIgnoreCase(provider)) {
            return new S3ReceivedFileStorage();
        }
        if ("AZURE".equalsIgnoreCase(provider)) {
            return new AzureReceivedFileStorage();
        }
        // Default to LOCAL for unknown values as well
        return new LocalReceivedFileStorage();
    }
}
