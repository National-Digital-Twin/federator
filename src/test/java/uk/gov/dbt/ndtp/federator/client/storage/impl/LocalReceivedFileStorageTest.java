package uk.gov.dbt.ndtp.federator.client.storage.impl;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;

/**
 * Unit tests for {@link LocalReceivedFileStorage} covering destination move, no-destination,
 * atomic move fallback, and failure scenarios. No reflection API is used.
 */
class LocalReceivedFileStorageTest {

    private Path tempDir;

    @AfterEach
    void cleanup() throws IOException {
        // Best-effort cleanup of any temp directories created by tests
        if (tempDir != null) {
            try {
                java.nio.file.Files.walk(tempDir)
                        .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                deleteIfExists(p);
                            } catch (IOException ignored) {
                                // Ignore cleanup errors
                            }
                        });
            } finally {
                tempDir = null;
            }
        }
    }

    @Test
    void store_destinationProvided_movesFile_success() throws IOException {
        tempDir = createTempDirectory("localrfst-");
        Path source = createTempFile(tempDir, "file-", ".bin");
        writeString(source, "content");

        // Destination is a full file path (including parent directories that may not exist yet)
        Path destParent = tempDir.resolve("nested/dir");
        Path dest = destParent.resolve("target.txt");

        LocalReceivedFileStorage storage = new LocalReceivedFileStorage();
        StoredFileResult result = storage.store(source, "ignored.txt", dest.toString());

        assertEquals(dest.toAbsolutePath(), result.localPath());
        assertTrue(isRegularFile(dest));
        assertFalse(exists(source), "Source temp file should be moved (deleted)");
        assertEquals("content", readString(dest));
    }

    @Test
    void store_destinationBlank_keepsOriginalLocation() throws IOException {
        tempDir = createTempDirectory("localrfst-");
        Path source = createTempFile(tempDir, "file-", ".bin");
        writeString(source, "abc");

        LocalReceivedFileStorage storage = new LocalReceivedFileStorage();
        // Blank destination
        StoredFileResult blankRes = storage.store(source, "f.txt", "   ");
        assertEquals(source.toAbsolutePath(), blankRes.localPath());
        assertTrue(exists(source));

        // Null destination
        StoredFileResult nullRes = storage.store(source, "f.txt", null);
        assertEquals(source.toAbsolutePath(), nullRes.localPath());
        assertTrue(exists(source));
    }

    @Test
    void store_atomicMoveFails_thenNonAtomicSucceeds_returnsTargetPath() throws Exception {
        tempDir = createTempDirectory("localrfst-");
        Path source = createTempFile(tempDir, "file-", ".bin");
        writeString(source, "data");

        Path dest = tempDir.resolve("dest.txt");
        createDirectories(dest.getParent());

        // We will mock java.nio.file.Files.move static method
        try (MockedStatic<java.nio.file.Files> files =
                Mockito.mockStatic(java.nio.file.Files.class, Mockito.CALLS_REAL_METHODS)) {
            // Let exists/createDirectories/read/write behave normally
            // Configure move: first call (with ATOMIC_MOVE) throws IOException, second call (without) succeeds
            files.when(() -> java.nio.file.Files.move(
                            Mockito.eq(source),
                            Mockito.eq(dest),
                            Mockito.eq(StandardCopyOption.REPLACE_EXISTING),
                            Mockito.eq(StandardCopyOption.ATOMIC_MOVE)))
                    .thenThrow(new IOException("Atomic move not supported"));

            files.when(() -> java.nio.file.Files.move(
                            Mockito.eq(source), Mockito.eq(dest), Mockito.eq(StandardCopyOption.REPLACE_EXISTING)))
                    .thenAnswer(inv -> {
                        // Simulate successful move by copying then deleting source
                        copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                        deleteIfExists(source);
                        return dest;
                    });

            LocalReceivedFileStorage storage = new LocalReceivedFileStorage();
            StoredFileResult res = storage.store(source, "f.txt", dest.toString());

            assertEquals(dest.toAbsolutePath(), res.localPath());
            assertTrue(exists(dest));
            assertFalse(exists(source));
        }
    }

    @Test
    void store_bothMovesFail_returnsOriginal_andKeepsFile() throws Exception {
        tempDir = createTempDirectory("localrfst-");
        Path source = createTempFile(tempDir, "file-", ".bin");
        writeString(source, "x");
        Path dest = tempDir.resolve("dest2.txt");

        try (MockedStatic<java.nio.file.Files> files =
                Mockito.mockStatic(java.nio.file.Files.class, Mockito.CALLS_REAL_METHODS)) {
            // First move with ATOMIC_MOVE fails
            files.when(() -> java.nio.file.Files.move(
                            Mockito.eq(source),
                            Mockito.eq(dest),
                            Mockito.eq(StandardCopyOption.REPLACE_EXISTING),
                            Mockito.eq(StandardCopyOption.ATOMIC_MOVE)))
                    .thenThrow(new IOException("Atomic move not supported"));
            // Fallback move without ATOMIC also fails
            files.when(() -> java.nio.file.Files.move(
                            Mockito.eq(source), Mockito.eq(dest), Mockito.eq(StandardCopyOption.REPLACE_EXISTING)))
                    .thenThrow(new FileAlreadyExistsException("simulated failure"));

            LocalReceivedFileStorage storage = new LocalReceivedFileStorage();
            StoredFileResult res = storage.store(source, "f.txt", dest.toString());

            // Should return the original path since move failed inside catch block
            assertEquals(source.toAbsolutePath(), res.localPath());
            assertTrue(exists(source), "Source should remain when both moves fail");
            assertFalse(exists(dest), "Destination should not be created on failure");
        }
    }
}
