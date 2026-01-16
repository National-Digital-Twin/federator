package uk.gov.dbt.ndtp.federator.client.grpc.file;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.exceptions.FileAssemblyException;
import uk.gov.dbt.ndtp.grpc.FileChunk;

class FileChunkAssemblerTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("federator-test-" + UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            // Clean up any files created under tempDir
            Files.walk(tempDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Test
    void test_accept_nullChunk() {
        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        assertThrows(NullPointerException.class, () -> assembler.accept(null));
    }

    @Test
    void test_accept_firstChunk_noSizeNoTotal() {
        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        FileChunk c1 = FileChunk.newBuilder()
                .setFileName("test.txt")
                .setFileSequenceId(1L)
                .setChunkIndex(0)
                .setIsLastChunk(false)
                .setChunkData(ByteString.copyFrom("data".getBytes()))
                .build();
        assertNull(assembler.accept(c1));
    }

    @Test
    void multiChunkAssembly_returnsPathOnlyAtEnd_andFileContentMatches() throws Exception {
        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);

        String fileName = "hello.txt";
        long seq = 1L;

        // First data chunk (not last)
        FileChunk c1 = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setChunkIndex(0)
                .setTotalChunks(2)
                .setIsLastChunk(false)
                .setFileSize(6)
                .setChunkData(ByteString.copyFrom("Hello ".getBytes()))
                .build();

        // Last chunk has no data for this implementation
        FileChunk c2 = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setChunkIndex(1)
                .setTotalChunks(2)
                .setIsLastChunk(true)
                .setFileSize(6)
                .build();

        assertNull(assembler.accept(c1), "Non-last chunk should return null");
        Path finalPath = assembler.accept(c2);
        assertNotNull(finalPath, "Last chunk should return final path");
        assertTrue(Files.exists(finalPath), "Final file should exist");

        String content = Files.readString(finalPath);
        assertEquals("Hello ", content, "Content should match bytes from data chunks only");
    }

    @Test
    void emptyFile_singleLastChunk_createsEmptyFile() throws Exception {
        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        String fileName = "empty.txt";
        long seq = 2L;

        // Expected checksum for empty content
        String emptyChecksum = GRPCUtils.calculateSha256Checksum(new byte[0]);

        FileChunk last = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setIsLastChunk(true)
                .setFileSize(0)
                .setTotalChunks(1)
                .setFileChecksum(emptyChecksum)
                .build();

        Path finalPath = assembler.accept(last);
        assertNotNull(finalPath);
        assertTrue(Files.exists(finalPath));
        assertEquals(0L, Files.size(finalPath));
    }

    @Test
    void checksumMismatch_throwsAndCleansTemp() {
        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        String fileName = "file.bin";
        long seq = 3L;

        // Send data in a non-last chunk
        FileChunk c1 = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setIsLastChunk(false)
                .setChunkIndex(0)
                .setTotalChunks(1)
                .setFileSize(3)
                .setChunkData(ByteString.copyFrom("abc".getBytes()))
                .build();

        assertNull(assembler.accept(c1));

        // Last chunk with an incorrect checksum
        FileChunk last = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setIsLastChunk(true)
                .setTotalChunks(1)
                .setFileSize(3)
                .setFileChecksum("deadbeef")
                .build();

        FileAssemblyException ex = assertThrows(FileAssemblyException.class, () -> assembler.accept(last));
        assertTrue(ex.getMessage().contains("Checksum mismatch"));

        // Ensure temp .part cleaned
        Path parts = tempDir.resolve(".parts");
        Path expectedTemp = parts.resolve(fileName + "." + seq + ".part");
        assertFalse(Files.exists(expectedTemp), "Temp part should be deleted on error");
    }

    @Test
    void sizeMismatch_throwsAndCleansTemp() {
        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        String fileName = "size.txt";
        long seq = 4L;

        // Data chunk declares size 3 but writes 4 bytes
        FileChunk c1 = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setIsLastChunk(false)
                .setChunkIndex(0)
                .setTotalChunks(2)
                .setFileSize(3)
                .setChunkData(ByteString.copyFrom("ABCD".getBytes()))
                .build();

        assertNull(assembler.accept(c1));

        // Last chunk closes and triggers size verification
        FileChunk last = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setIsLastChunk(true)
                .setTotalChunks(2)
                .setFileSize(3)
                .build();

        FileAssemblyException ex = assertThrows(FileAssemblyException.class, () -> assembler.accept(last));
        assertTrue(ex.getMessage().contains("Size mismatch"));

        // Ensure temp .part cleaned
        Path parts = tempDir.resolve(".parts");
        Path expectedTemp = parts.resolve(fileName + "." + seq + ".part");
        assertFalse(Files.exists(expectedTemp), "Temp part should be deleted on error");
    }

    @Test
    void testConstructors() {
        assertNotNull(new FileChunkAssembler());
        assertNotNull(new FileChunkAssembler(tempDir.toString()));
        assertNotNull(new FileChunkAssembler(tempDir, "dest"));
    }

    @Test
    void testSanitize() {
        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        // We can't access sanitize directly as it is private, but we can test it through accept if it's used.
        // Actually FileChunkAssembler structure shows it's private.
        // Let's use reflection if we want to be sure, or just test with a fileName that should be sanitized.

        FileChunk c1 = FileChunk.newBuilder()
                .setFileName("../dangerous.txt")
                .setFileSequenceId(10L)
                .setIsLastChunk(true)
                .setFileSize(0)
                .setTotalChunks(1)
                .build();

        Path result = assembler.accept(c1);
        assertEquals("dangerous.txt", result.getFileName().toString());
    }

    @Test
    void testHandleDataChunk_IOException() throws Exception {
        // Mocking behavior that causes IOException during write is hard without mocking FileSystems.
        // But we can try to make the part file a directory to cause IOException.
        Path parts = tempDir.resolve(".parts");
        Files.createDirectories(parts);
        String fileName = "ioerror.txt";
        long seq = 5L;
        Path partFile = parts.resolve(fileName + "." + seq + ".part");
        Files.createDirectories(partFile); // Part file is now a directory, write should fail.

        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        FileChunk c1 = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(seq)
                .setIsLastChunk(false)
                .setChunkIndex(0)
                .setTotalChunks(1)
                .setChunkData(ByteString.copyFrom("abc".getBytes()))
                .build();

        // SneakyThrows just throws the raw IOException (FileNotFoundException in this case)
        assertThrows(java.io.IOException.class, () -> assembler.accept(c1));
    }

    @Test
    void testHandleLastChunk_MoveFails() throws Exception {
        // To make move fail, we can try to make the destination an existing non-empty directory.
        // Files.move with REPLACE_EXISTING might still fail if it's a non-empty directory.
        String fileName = "movefail.txt";
        Path destDir = tempDir.resolve(fileName);
        Files.createDirectories(destDir);
        Files.createFile(destDir.resolve("notempty.txt"));

        FileChunkAssembler assembler = new FileChunkAssembler(tempDir);
        FileChunk c1 = FileChunk.newBuilder()
                .setFileName(fileName)
                .setFileSequenceId(6L)
                .setIsLastChunk(true)
                .setFileSize(0)
                .setTotalChunks(1)
                .build();

        // SneakyThrows will throw IOException if move fails.
        // But wait, the code wraps the move in a try-catch for IOException to retry without ATOMIC_MOVE.
        // If that also fails, it will throw the IOException.
        assertThrows(java.io.IOException.class, () -> assembler.accept(c1));
    }
}
