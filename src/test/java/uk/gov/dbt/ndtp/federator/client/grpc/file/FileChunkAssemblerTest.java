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
                        }
                    });
        }
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
    void checksumMismatch_throwsAndCleansTemp() throws Exception {
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
    void sizeMismatch_throwsAndCleansTemp() throws Exception {
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
}
