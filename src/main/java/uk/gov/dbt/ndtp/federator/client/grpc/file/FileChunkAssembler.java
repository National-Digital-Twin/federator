// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.client.grpc.file;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.move;
import static java.nio.file.Files.size;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorage;
import uk.gov.dbt.ndtp.federator.client.storage.ReceivedFileStorageFactory;
import uk.gov.dbt.ndtp.federator.client.storage.StoredFileResult;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.FileAssemblyException;
import uk.gov.dbt.ndtp.grpc.FileChunk;

/**
 * Disk-backed assembler for incoming FileChunk messages.
 * Writes chunks directly to disk to support very large files without large memory footprint.
 * Completed files are stored in a temp directory that defaults to
 * {@code client.files.temp.dir} (from client.properties). If the property is blank or missing,
 * falls back to {@code ${java.io.tmpdir}/federator-files}.
 */
@Slf4j
public class FileChunkAssembler {

    private final Path baseTempDir;
    private final Map<String, AssemblyState> assemblies = new HashMap<>();
    private final String destinationHint;

    /**
     * Creates an assembler that writes to the default temp directory resolved from
     * {@code client.files.temp.dir} with fallback to {@code ${java.io.tmpdir}/federator-files}.
     */
    public FileChunkAssembler() {
        this(resolveDefaultTempDir(), null);
    }

    /**
     * Creates an assembler that writes to the provided base directory.
     * The directory and its internal {@code .parts} folder are created if needed.
     *
     * @param baseTempDir base directory used to store final files and temporary parts
     * @throws IllegalStateException if the directories cannot be created
     */
    public FileChunkAssembler(Path baseTempDir) {
        this(baseTempDir, null);
    }

    /**
     * Creates an assembler that writes to the default temp directory and forwards the provided
     * destination hint to the storage provider.
     */
    public FileChunkAssembler(String destinationHint) {
        this(resolveDefaultTempDir(), destinationHint);
    }

    /**
     * Creates an assembler with explicit base directory and destination hint for final storage.
     *
     * @param baseTempDir base directory used to store final files and temporary parts
     * @param destinationHint destination hint to be forwarded to storage provider (e.g., local dir or s3://bucket/prefix)
     */
    public FileChunkAssembler(Path baseTempDir, String destinationHint) {
        this.baseTempDir = baseTempDir;
        this.destinationHint = destinationHint;
        ensureDir(baseTempDir);
        ensureDir(baseTempDir.resolve(".parts"));
    }

    private static Path resolveDefaultTempDir() {
        try {
            String configured = PropertyUtil.getPropertyValue("client.files.temp.dir", "");
            if (configured != null && !configured.isBlank()) {
                return get(configured);
            }
        } catch (RuntimeException ignored) {
            // PropertyUtil may not be initialized in some tests; fall back to system tmp
        }
        String tmp = System.getProperty("java.io.tmpdir");
        return get(tmp, "federator-files");
    }

    /**
     * Process a single file chunk. When the last chunk for a file is received, finalizes the file and
     * returns the absolute path to the stored file. Otherwise returns null.
     */
    /**
     * Accepts a single {@link FileChunk} message and writes its data to disk. When the last chunk of a
     * file is received, the temporary parts file is moved to the final target name and the configured
     * storage provider is invoked (LOCAL or S3). For non-final chunks this method returns {@code null}.
     *
     * @param chunk the incoming file chunk
     * @return the absolute {@link Path} of the completed file when the last chunk is processed; otherwise {@code null}
     * @throws FileAssemblyException when integrity checks (checksum/size) fail on the last chunk
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
            createDirectories(dir);
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
        log.debug(
                "Received chunk {} of {} for {} ({} bytes)",
                chunk.getChunkIndex(),
                state.expectedChunks,
                fileName,
                data.length);
        return null;
    }

    private Path handleLastChunk(FileChunk chunk, String fileName, String key, AssemblyState state, long seqId) {
        // Last chunk. This may be the only message for empty files.
        if (state == null) {
            state = startAssembly(fileName, seqId, chunk);
            assemblies.put(key, state);
        }
        closeQuietly(state);

        verifyChecksumIfProvided(chunk, state, key, fileName, seqId);
        verifySizeIfProvided(chunk, state, key, fileName);

        Path finalTarget = moveToFinalTarget(state, fileName);

        // Delegate storage (LOCAL or S3) based on configuration
        ReceivedFileStorage storage = ReceivedFileStorageFactory.get();
        StoredFileResult storeResult = storage.store(finalTarget, fileName, destinationHint);
        storeResult.remoteUriOpt().ifPresent(uri -> log.info("Remote location: {}", uri));

        assemblies.remove(key);
        Path localStoredPath = storeResult.localPath();
        Path absolutePath = localStoredPath.toAbsolutePath();
        logStoredFileInfo(absolutePath);
        return absolutePath;
    }

    private void logStoredFileInfo(Path absolutePath) {
        try {
            if (exists(absolutePath)) {
                log.info("Stored received file at: {} ({} bytes)", absolutePath, size(absolutePath));
            } else {
                log.info("Stored received file; local temp removed by provider. Last known path: {}", absolutePath);
            }
        } catch (IOException ioe) {
            log.info("Stored received file at: {} (size unavailable)", absolutePath);
        }
    }

    private void verifyChecksumIfProvided(
            FileChunk chunk, AssemblyState state, String key, String fileName, long seqId) {
        String expectedChecksum = chunk.getFileChecksum();
        String actualChecksum = GRPCUtils.calculateSha256Checksum(state.tempFile);
        log.info("Expected checksum: {}, actual checksum: {}", expectedChecksum, actualChecksum);
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
        long actualSize = exists(state.tempFile) ? size(state.tempFile) : 0L;
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
            move(state.tempFile, finalTarget, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (IOException moveEx) {
            // Retry without ATOMIC_MOVE in case filesystem doesn't support it
            move(state.tempFile, finalTarget, REPLACE_EXISTING);
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
                deleteIfExists(state.tempFile);
            }
        } catch (IOException ignore) {
            log.warn("Failed to delete temp file {} after error", state.tempFile);
        }
        assemblies.remove(key);
    }

    private void closeQuietly(AssemblyState state) {
        if (state != null && state.out != null) {
            try {
                state.out.flush();
                state.out.close();
            } catch (IOException e) {
                log.debug("Error closing stream for {} seq {}", state.fileName, state.sequenceId, e);
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
