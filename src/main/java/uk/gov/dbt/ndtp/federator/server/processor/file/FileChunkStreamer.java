// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.server.processor.file;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProvider;
import uk.gov.dbt.ndtp.federator.common.storage.provider.file.FileProviderFactory;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileChunk;

/**
 * Streams file content in chunks to a gRPC stream observer.
 * Extracted from FileKafkaEventMessageProcessor to keep responsibilities modular.
 */
public class FileChunkStreamer {
    private static final Logger LOGGER = LoggerFactory.getLogger("FileChunkStreamer");

    private final int chunkSize;

    public FileChunkStreamer(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * Streams the file specified in the FileTransferRequest to the provided StreamObservable in chunks.
     * Each chunk is sent as a FileChunk message.
     * @param fileSequenceId
     * @param fileTransferRequest
     * @param streamObserver
     */
    public void stream(
            long fileSequenceId, FileTransferRequest fileTransferRequest, StreamObservable<FileChunk> streamObserver) {
        File file = new File(fileTransferRequest.path());

        try (FileTransferResult fetchResult = fetch(fileTransferRequest)) {
            long fileSize = fetchResult.fileSize();
            int totalChunks = computeTotalChunks(fileSize);
            try (InputStream is = fetchResult.stream()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                int lastChunkIndex = readAndStreamChunks(
                        is, file.getName(), fileSize, totalChunks, fileSequenceId, streamObserver, digest);
                String checksum = encodeChecksum(digest);
                streamObserver.onNext(buildLastChunk(
                        file.getName(), lastChunkIndex, fileSize, checksum, fileSequenceId, totalChunks));
                LOGGER.info("Completed sending file sequence_id : {} ", fileSequenceId);
            }
        } catch (Exception e) {
            handleError(fileSequenceId, e, streamObserver);
        }
    }

    private FileTransferResult fetch(FileTransferRequest request) {
        FileProvider fileProvider = FileProviderFactory.getProvider(request.sourceType());
        return fileProvider.get(request);
    }

    private int computeTotalChunks(long fileSize) {
        return Math.toIntExact(fileSize / chunkSize + (fileSize % chunkSize == 0 ? 0 : 1));
    }

    @SneakyThrows
    private int readAndStreamChunks(
            InputStream is,
            String fileName,
            long fileSize,
            int totalChunks,
            long fileSequenceId,
            StreamObservable<FileChunk> observer,
            MessageDigest digest) {
        byte[] buffer = new byte[chunkSize];
        int bytesRead;
        int chunkIndex = 0;

        while ((bytesRead = is.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
            observer.onNext(
                    buildDataChunk(fileName, buffer, bytesRead, chunkIndex, totalChunks, fileSize, fileSequenceId));
            logProgress(chunkIndex, totalChunks, fileSequenceId);
            chunkIndex++;
        }
        return chunkIndex;
    }

    private FileChunk buildDataChunk(
            String fileName,
            byte[] buffer,
            int bytesRead,
            int chunkIndex,
            int totalChunks,
            long fileSize,
            long fileSequenceId) {
        return FileChunk.newBuilder()
                .setFileName(fileName)
                .setChunkData(ByteString.copyFrom(buffer, 0, bytesRead))
                .setChunkIndex(chunkIndex)
                .setTotalChunks(totalChunks)
                .setFileSize(fileSize)
                .setIsLastChunk(false)
                .setFileSequenceId(fileSequenceId)
                .build();
    }

    private FileChunk buildLastChunk(
            String fileName,
            int nextChunkIndex,
            long fileSize,
            String fileChecksum,
            long fileSequenceId,
            int totalChunks) {
        return FileChunk.newBuilder()
                .setFileName(fileName)
                .setChunkIndex(nextChunkIndex)
                .setIsLastChunk(true)
                .setFileChecksum(fileChecksum)
                .setFileSize(fileSize)
                .setFileSequenceId(fileSequenceId)
                .setTotalChunks(totalChunks)
                .build();
    }

    private void logProgress(int chunkIndex, int totalChunks, long fileSequenceId) {
        if (chunkIndex % 2 == 0) {
            LOGGER.debug("Sent chunk {}/{} for file sequence_id : {} ", chunkIndex, totalChunks, fileSequenceId);
        }
    }

    private String encodeChecksum(MessageDigest digest) {
        return GRPCUtils.bytesToHex(digest.digest());
    }

    private void handleError(long fileSequenceId, Exception e, StreamObservable<FileChunk> streamObserver) {
        LOGGER.error("Error processing file sequence_id : {} ", fileSequenceId, e);
        streamObserver.onError(Status.INTERNAL
                .withDescription("Failed processing file or Kafka stream")
                .withCause(e)
                .asRuntimeException());
    }
}
