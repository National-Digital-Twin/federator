package uk.gov.dbt.ndtp.federator.processor;

import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Reads a file path from the Kafka event value and streams the file contents as one or more
 * {@link KafkaByteBatch} messages to the provided {@link StreamObservable}.
 * <p>
 * The processor reads files in configurable chunk sizes to avoid loading entire files into memory.
 */
public class FileKafkaEventMessageProcessor implements MessageProcessor<KafkaEvent<String, String>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("FileKafkaEventMessageProcessor");
    private static final int CHUNK_SIZE = 1000; // 10 bytes for testing, adjust as needed
    private final StreamObservable<FileChunk> serverCallStreamObserver;

    /**
     * Create a processor with optional base directory and chunk size.
     */
    public FileKafkaEventMessageProcessor(StreamObservable<FileChunk> serverCallStreamObserver) {
        this.serverCallStreamObserver = Objects.requireNonNull(serverCallStreamObserver, "serverCallStreamObserver");
    }

    @Override
    public void process(KafkaEvent<String, String> kafkaEvent) {
        LOGGER.info(
                "Processing file sequence_id : {} ",
                kafkaEvent.getConsumerRecord().offset());
        String filePath = kafkaEvent.value();
        streamFiles(kafkaEvent.getConsumerRecord().offset(), filePath);
        LOGGER.info("File sequence id : {} ", filePath);
    }

    private void streamFiles(long filSequenceId, String filePath) {

        File file = new File(filePath);
        long fileSize = file.length();
        // calculate number of chunks in integer

        var totalChuncks = Math.toIntExact(fileSize / CHUNK_SIZE + (fileSize % CHUNK_SIZE == 0 ? 0 : 1));

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);

                FileChunk chunk = FileChunk.newBuilder()
                        .setFileName(file.getName())
                        .setChunkData(ByteString.copyFrom(buffer, 0, bytesRead))
                        .setChunkIndex(chunkIndex)
                        .setTotalChunks(totalChuncks)
                        .setFileSize(fileSize)
                        .setIsLastChunk(false)
                        .setFileSequenceId(filSequenceId)
                        .build();

                serverCallStreamObserver.onNext(chunk);
                chunkIndex++;
            }

            String fileChecksum = Base64.getEncoder().encodeToString(digest.digest());
            serverCallStreamObserver.onNext(FileChunk.newBuilder()
                    .setFileName(file.getName())
                    .setChunkIndex(chunkIndex)
                    .setIsLastChunk(true)
                    .setFileChecksum(fileChecksum)
                    .setFileSize(fileSize)
                    .build());

            serverCallStreamObserver.onCompleted();

        } catch (Exception e) {
            LOGGER.error("Error processing file sequence_id : {} ", filSequenceId, e);
            serverCallStreamObserver.onError(e);
        }
    }
}
