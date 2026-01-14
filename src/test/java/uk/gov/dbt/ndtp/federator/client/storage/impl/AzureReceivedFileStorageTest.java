package uk.gov.dbt.ndtp.federator.client.storage.impl;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.writeString;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

class AzureReceivedFileStorageTest {

    @AfterEach
    void tearDown() {
        try {
            PropertyUtil.clear();
        } catch (Exception ignored) {
            // ignore if not initialized
        }
    }

    @Test
    void resolveContainer_returnsEmptyWhenNotConfigured() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.azure.container", ""))
                    .thenReturn("");

            AzureReceivedFileStorage az = new AzureReceivedFileStorage();
            assertEquals("", az.resolveContainer());
        }
    }

    @Test
    void resolveContainer_returnsConfiguredContainer() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.azure.container", ""))
                    .thenReturn("team-container");

            AzureReceivedFileStorage az = new AzureReceivedFileStorage();
            assertEquals("team-container", az.resolveContainer());
        }
    }

    @Test
    void resolveKey_handlesNullBlankAndPrefixesAndNormalization() {
        AzureReceivedFileStorage az = new AzureReceivedFileStorage();

        assertEquals("name.txt", az.resolveKey(null, "dir/name.txt"));
        assertEquals("name.txt", az.resolveKey("   ", "x/../name.txt"));
        assertEquals("a/b/name.txt", az.resolveKey("a/b/", "c/d/name.txt"));
        assertEquals("a/b/c.txt", az.resolveKey("/a/b/c.txt", "ignored.txt"));
    }

    @Test
    void store_containerBlank_skipsUpload_andKeepsLocalFile() throws IOException {
        Path temp = createTempFile("azrfst-", ".bin");
        writeString(temp, "data");

        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.azure.container", ""))
                    .thenReturn("");

            AzureReceivedFileStorage az = new AzureReceivedFileStorage();
            StoredFileResult res = az.store(temp, "f.txt", null);
            assertTrue(exists(res.localPath()));
            assertFalse(res.remoteUriOpt().isPresent());
        } finally {
            deleteIfExists(temp);
        }
    }

    @Test
    void store_success_deletesLocal_andReturnsRemoteUri() throws IOException {
        Path temp = createTempFile("azrfst-", ".bin");
        writeString(temp, "data");

        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.azure.container", ""))
                    .thenReturn("team-container");

            class TestAzure extends AzureReceivedFileStorage {
                @Override
                String upload(Path localFile, String container, String blobPath) {
                    return String.format("azure://%s/%s", container, blobPath);
                }
            }

            AzureReceivedFileStorage az = new TestAzure();
            StoredFileResult res = az.store(temp, "file.txt", "prefix/");

            assertTrue(res.remoteUriOpt().isPresent());
            assertFalse(exists(temp), "Temp file should be deleted after successful upload");
        } finally {
            deleteIfExists(temp);
        }
    }

    @Test
    void store_failure_deletesLocal_andNoRemoteUri() throws IOException {
        Path temp = createTempFile("azrfst-", ".bin");
        writeString(temp, "data");

        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.azure.container", ""))
                    .thenReturn("team-container");

            class TestAzure extends AzureReceivedFileStorage {
                @Override
                String upload(Path localFile, String container, String blobPath) {
                    throw new RuntimeException("simulated Azure error");
                }
            }

            AzureReceivedFileStorage az = new TestAzure();
            StoredFileResult res = az.store(temp, "file.txt", "prefix/");

            assertFalse(res.remoteUriOpt().isPresent());
            assertFalse(exists(temp), "Temp file should be deleted when upload fails");
        } finally {
            deleteIfExists(temp);
        }
    }
}
