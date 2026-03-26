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

class GCPReceivedFileStorageTest {
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
    void resolveBucket_returnsEmptyWhenNotConfigured() {
        // Mock PropertyUtil to return blank bucket
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.gcp.bucket", ""))
                    .thenReturn("");

            GCPReceivedFileStorage gcp = new GCPReceivedFileStorage();
            assertEquals("", gcp.resolveBucket());
        }
    }

    @Test
    void resolveBucket_returnsConfiguredBucket() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.gcp.bucket", ""))
                    .thenReturn("my-team-docs");

            GCPReceivedFileStorage gcp = new GCPReceivedFileStorage();
            assertEquals("my-team-docs", gcp.resolveBucket());
        }
    }

    @Test
    void resolveKey_handlesNullBlankAndPrefixesAndNormalization() {
        GCPReceivedFileStorage gcp = new GCPReceivedFileStorage();

        // null destination -> sanitized original file name
        assertEquals("name.txt", gcp.resolveKey(null, "dir/name.txt"));
        // blank destination -> sanitized
        assertEquals("name.txt", gcp.resolveKey("   ", "x/../name.txt"));
        // prefix with trailing slash -> append sanitized file name
        assertEquals("a/b/name.txt", gcp.resolveKey("a/b/", "c/d/name.txt"));
        // full key without trailing slash -> normalize leading slashes are removed
        assertEquals("a/b/c.txt", gcp.resolveKey("/a/b/c.txt", "ignored.txt"));
    }

    // We avoid direct testing of upload() to prevent static initialization of GcsClientFactory.
    // Instead, we exercise store() behavior with a subclass overriding upload().

    @Test
    void store_bucketBlank_skipsUpload_andKeepsLocalFile() throws IOException {
        Path temp = createTempFile("gcprfst-", ".bin");
        writeString(temp, "data");

        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.gcp.bucket", ""))
                    .thenReturn("");
            GCPReceivedFileStorage gcp = new GCPReceivedFileStorage();
            StoredFileResult res = gcp.store(temp, "f.txt", null);
            assertTrue(exists(res.localPath()));
            assertFalse(res.remoteUriOpt().isPresent());
        } finally {
            deleteIfExists(temp);
        }
    }

    @Test
    void store_success_deletesLocal_andReturnsRemoteUri() throws IOException {
        Path temp = createTempFile("gcprfst-", ".bin");
        writeString(temp, "data");

        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            // Bucket resolution
            prop.when(() -> PropertyUtil.getPropertyValue("files.gcp.bucket", ""))
                    .thenReturn("my-team-docs");
            // Use a test subclass that fakes upload success
            class TestGCP extends GCPReceivedFileStorage {
                @Override
                String upload(Path localFile, String bucket, String key) {
                    return String.format("gs://%s/%s", bucket, key);
                }
            }
            GCPReceivedFileStorage gcp = new TestGCP();
            StoredFileResult res = gcp.store(temp, "file.txt", "prefix/");

            assertTrue(res.remoteUriOpt().isPresent());
            assertFalse(exists(temp), "Temp file should be deleted after successful upload");
        } finally {
            deleteIfExists(temp);
        }
    }

    @Test
    void store_failure_deletesLocal_andNoRemoteUri() throws IOException {
        Path temp = createTempFile("gcprfst-", ".bin");
        writeString(temp, "data");

        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            // Bucket resolution
            prop.when(() -> PropertyUtil.getPropertyValue("files.gcp.bucket", ""))
                    .thenReturn("my-team-docs");
            // Use a test subclass that simulates upload failure by throwing an exception
            class TestGCP extends GCPReceivedFileStorage {
                @Override
                String upload(Path localFile, String bucket, String key) {
                    throw new RuntimeException("simulated GCS error");
                }
            }
            GCPReceivedFileStorage gcp = new TestGCP();
            StoredFileResult res = gcp.store(temp, "file.txt", "prefix/");

            assertFalse(res.remoteUriOpt().isPresent());
            assertFalse(exists(temp), "Temp file should be deleted when upload fails");
        } finally {
            deleteIfExists(temp);
        }
    }

    @Test
    void resolveBucket_returnsEmptyWhenNullReturned() {
        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            prop.when(() -> PropertyUtil.getPropertyValue("files.gcp.bucket", ""))
                    .thenReturn(null);

            GCPReceivedFileStorage gcp = new GCPReceivedFileStorage();
            assertEquals("", gcp.resolveBucket());
        }
    }

    @Test
    void store_uploadReturnsNull_deletesLocal_andNoRemoteUri() throws IOException {
        Path temp = createTempFile("gcprfst-", ".bin");
        writeString(temp, "data");

        try (MockedStatic<PropertyUtil> prop = Mockito.mockStatic(PropertyUtil.class)) {
            // Bucket resolution
            prop.when(() -> PropertyUtil.getPropertyValue("files.gcp.bucket", ""))
                    .thenReturn("my-team-docs");
            // Use a test subclass that simulates upload failure by returning null
            class TestGCP extends GCPReceivedFileStorage {
                @Override
                String upload(Path localFile, String bucket, String key) {
                    return null; // Simulate upload failure without exception
                }
            }
            GCPReceivedFileStorage gcp = new TestGCP();
            StoredFileResult res = gcp.store(temp, "file.txt", "prefix/");

            assertFalse(res.remoteUriOpt().isPresent());
            assertFalse(exists(temp), "Temp file should be deleted when upload returns null");
        } finally {
            deleteIfExists(temp);
        }
    }
}
