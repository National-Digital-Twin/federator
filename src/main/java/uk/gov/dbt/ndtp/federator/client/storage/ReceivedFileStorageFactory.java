package uk.gov.dbt.ndtp.federator.client.storage;

import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.storage.impl.LocalReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.impl.S3ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

/**
 * Factory for selecting a {@link uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorage}
 * implementation based on {@code client.files.storage.provider} (LOCAL|S3).
 *
 * <p>Defaults to LOCAL when the property is missing or has an unknown value.</p>
 */
@Slf4j
public final class ReceivedFileStorageFactory {

    private static final String STORAGE_PROVIDER_PROP = "client.files.storage.provider"; // LOCAL | S3

    private ReceivedFileStorageFactory() {}

    /**
     * Returns a storage implementation based on {@code client.files.storage.provider}.
     *
     * @return {@link S3ReceivedFileStorage} when provider is {@code S3}; otherwise {@link LocalReceivedFileStorage}
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
        // Default to LOCAL for unknown values as well
        return new LocalReceivedFileStorage();
    }
}
