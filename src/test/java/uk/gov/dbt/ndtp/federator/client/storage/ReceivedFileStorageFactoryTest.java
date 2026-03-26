package uk.gov.dbt.ndtp.federator.client.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.client.storage.impl.AzureReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.impl.GCPReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.impl.LocalReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.impl.S3ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

class ReceivedFileStorageFactoryTest {

    @AfterEach
    void tearDown() {
        // Ensure no lingering global state between tests
        try {
            PropertyUtil.clear();
        } catch (Exception ignored) {
            // ignore if not initialized
        }
    }

    @Test
    void get_returnsS3Storage_whenProviderIsS3() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("S3");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(S3ReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsS3Storage_whenProviderIsS3CaseInsensitive() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("s3");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(S3ReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsAzureStorage_whenProviderIsAzure() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("AZURE");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(AzureReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsAzureStorage_whenProviderIsAzureCaseInsensitive() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("Azure");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(AzureReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsGCPStorage_whenProviderIsGCP() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("GCP");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(GCPReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsGCPStorage_whenProviderIsGCPCaseInsensitive() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("gcp");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(GCPReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsLocalStorage_whenProviderIsLocal() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("LOCAL");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(LocalReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsLocalStorage_whenProviderIsEmpty() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(LocalReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsLocalStorage_whenProviderIsNull() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn(null);

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(LocalReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsLocalStorage_whenProviderIsUnknown() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenReturn("UNKNOWN_PROVIDER");

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(LocalReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsLocalStorage_whenPropertyUtilThrowsException() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenThrow(new RuntimeException("Property not available"));

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(LocalReceivedFileStorage.class, storage);
        }
    }

    @Test
    void get_returnsLocalStorage_whenPropertyUtilThrowsRuntimeException() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("client.files.storage.provider", "LOCAL"))
                    .thenThrow(new RuntimeException("Configuration error"));

            ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
            assertInstanceOf(LocalReceivedFileStorage.class, storage);
        }
    }
}
