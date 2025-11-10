// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.client.grpc.file;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.FileAssemblyException;
import uk.gov.dbt.ndtp.grpc.FileChunk;

/**
 * Disk-backed assembler for incoming FileChunk messages.
 * Writes chunks directly to disk to support very large files without large memory footprint.
 * Completed files are stored in java.io.tmpdir/federator-files by default.
 */
public class FileChunkAssembler {
    private static final Logger LOGGER = LoggerFactory.getLogger("FileChunkAssembler");
    private static final String FILES_TEMP_DIR_PROP = "client.files.temp.dir";

    private final Path baseTempDir;
    private final Map<String, AssemblyState> assemblies = new HashMap<>();

    public FileChunkAssembler() {
        this(resolveDefaultTempDir());
    }

    public FileChunkAssembler(Path baseTempDir) {
        this.baseTempDir = baseTempDir;
        ensureDir(baseTempDir);
        ensureDir(baseTempDir.resolve(".parts"));
    }

    private static Path resolveDefaultTempDir() {
        String tmp = System.getProperty("java.io.tmpdir");
        Path defaultDir = Paths.get(tmp, "federator-files");
        try {
            String configured = PropertyUtil.getPropertyValue(FILES_TEMP_DIR_PROP, defaultDir.toString());
            Path configuredPath = Paths.get(configured);
            return configuredPath.isAbsolute() ? configuredPath : configuredPath.toAbsolutePath();
        } catch (RuntimeException ex) {
            LOGGER.debug(
                    "Property '{}' not set or PropertyUtil not initialized. Using default temp dir: {}",
                    FILES_TEMP_DIR_PROP,
                    defaultDir,
                    ex);
            return defaultDir;
        }
    }

    /**
     * Process a single file chunk. When the last chunk for a file is received, finalizes the file and
     * returns the absolute path to the stored file. Otherwise returns null.
     */
    @SneakyThrows
    public synchronized Path accept(FileChunk chunk) {
        String fileName = chunk.getFileName();
        long seqId = chunk.getFileSequenceId();
        String key = buildKey(fileName, seqId);
        AssemblyState state = assemblies.get(key);

        if (!chunk.getIsLastChunk()) {
            return handleDataChunk(chunk, fileName, key, state, seqId);
        }
        return handleLastChunk(chunk, fileName, key, state, seqId);
    }

    private void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create temp directory: " + dir, e);
        }
    }

    @SneakyThrows
    private Path handleDataChunk(FileChunk chunk, String fileName, String key, AssemblyState state, long seqId) {
        if (state == null) {
            state = startAssembly(fileName, seqId, chunk);
            assemblies.put(key, state);
        }
        byte[] data = chunk.getChunkData().toByteArray();
        state.out.write(data);
        state.bytesWritten += data.length;
        if (state.expectedSize < 0) state.expectedSize = chunk.getFileSize();
        if (state.expectedChunks < 0) state.expectedChunks = chunk.getTotalChunks();
        LOGGER.debug(
                "Received chunk {} of {} for {} ({} bytes)",
                chunk.getChunkIndex(),
                state.expectedChunks,
                fileName,
                data.length);
        return null;
    }

    private Path handleLastChunk(FileChunk chunk, String fileName, String key, AssemblyState state, long seqId)
            throws IOException {
        // Last chunk. This may be the only message for empty files.
        if (state == null) {
            state = startAssembly(fileName, seqId, chunk);
            assemblies.put(key, state);
        }
        closeQuietly(state);

        verifyChecksumIfProvided(chunk, state, key, fileName, seqId);
        verifySizeIfProvided(chunk, state, key, fileName);

        Path finalTarget = moveToFinalTarget(state, fileName);
        assemblies.remove(key);
        LOGGER.info("Stored received file at: {} ({} bytes)", finalTarget.toAbsolutePath(), Files.size(finalTarget));
        return finalTarget.toAbsolutePath();
    }

    private void verifyChecksumIfProvided(
            FileChunk chunk, AssemblyState state, String key, String fileName, long seqId) {
        String expectedChecksum = chunk.getFileChecksum();
        String actualChecksum = GRPCUtils.calculateSha256Checksum(state.tempFile);
        LOGGER.info("Expected checksum: {}, actual checksum: {}", expectedChecksum, actualChecksum);
        if (!expectedChecksum.isBlank() && !expectedChecksum.equalsIgnoreCase(actualChecksum)) {
            cleanupOnError(key, state);
            throw new FileAssemblyException("Checksum mismatch for file " + fileName + " (seq=" + seqId + ")");
        }
    }

    @SneakyThrows
    private void verifySizeIfProvided(FileChunk chunk, AssemblyState state, String key, String fileName) {
        if (state.expectedSize < 0 && chunk.getFileSize() >= 0) {
            state.expectedSize = chunk.getFileSize();
        }
        long actualSize = Files.exists(state.tempFile) ? Files.size(state.tempFile) : 0L;
        if (state.expectedSize >= 0 && actualSize != state.expectedSize) {
            cleanupOnError(key, state);
            throw new FileAssemblyException(
                    "Size mismatch for file " + fileName + ": expected " + state.expectedSize + ", got " + actualSize);
        }
    }

    @SneakyThrows
    private Path moveToFinalTarget(AssemblyState state, String fileName) {
        Path finalTarget = baseTempDir.resolve(sanitize(fileName));
        try {
            Files.move(
                    state.tempFile, finalTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException moveEx) {
            // Retry without ATOMIC_MOVE in case filesystem doesn't support it
            Files.move(state.tempFile, finalTarget, StandardCopyOption.REPLACE_EXISTING);
        }
        return finalTarget;
    }

    @SneakyThrows
    private AssemblyState startAssembly(String fileName, long seqId, FileChunk firstChunk) {
        AssemblyState state = new AssemblyState(fileName, seqId);
        Path partsDir = baseTempDir.resolve(".parts");
        String safeName = sanitize(fileName);
        Path temp = partsDir.resolve(safeName + "." + seqId + ".part");
        // Ensure parent dir exists
        ensureDir(partsDir);
        state.tempFile = temp;
        state.out = new BufferedOutputStream(new FileOutputStream(temp.toFile()));
        if (firstChunk.getFileSize() > 0) state.expectedSize = firstChunk.getFileSize();
        if (firstChunk.getTotalChunks() > 0) state.expectedChunks = firstChunk.getTotalChunks();
        return state;
    }

    private void cleanupOnError(String key, AssemblyState state) {
        closeQuietly(state);
        try {
            if (state.tempFile != null) {
                Files.deleteIfExists(state.tempFile);
            }
        } catch (IOException ignore) {
            LOGGER.warn("Failed to delete temp file {} after error", state.tempFile);
        }
        assemblies.remove(key);
    }

    private void closeQuietly(AssemblyState state) {
        if (state != null && state.out != null) {
            try {
                state.out.flush();
                state.out.close();
            } catch (IOException e) {
                LOGGER.debug("Error closing stream for {} seq {}", state.fileName, state.sequenceId, e);
            }
        }
    }

    private String sanitize(String name) {
        // Prevent path traversal and normalize separators
        return new File(name).getName();
    }

    private String buildKey(String fileName, long seqId) {
        return sanitize(fileName) + "#" + seqId;
    }

    private static class AssemblyState {
        final long sequenceId;
        final String fileName;
        OutputStream out; // open stream to temp .part file
        Path tempFile;
        long expectedSize = -1;
        int expectedChunks = -1;
        long bytesWritten = 0;

        AssemblyState(String fileName, long sequenceId) {
            this.sequenceId = sequenceId;
            this.fileName = fileName;
        }
    }
}
