package uk.gov.dbt.ndtp.federator.server.processor.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.exception.FileTransferException;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.utils.ObjectMapperUtil;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.grpc.FileStreamEvent;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.StreamWarning;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Reads a file path from the Kafka event value and streams the file contents as one or more
 * {@link KafkaByteBatch} messages to the provided {@link StreamObservable}.
 * <p>
 * The processor reads files in configurable chunk sizes to avoid loading entire files into memory.
 */
public class FileKafkaEventMessageProcessor implements MessageProcessor<KafkaEvent<String, byte[]>> {
    public static final String DEFAULT_ONE_MB_SIZE = "1000000";
    private static final Logger LOGGER = LoggerFactory.getLogger("FileKafkaEventMessageProcessor");
    private static final String CHUNK_SIZE = "file.stream.chunk.size";
    private final StreamObservable<FileStreamEvent> serverCallStreamObserver;
    private final FileChunkStreamer fileChunkStreamer;
    private final FileTransferRequestValidator validator;

    /**
     * Constructor for FileKafkaEventMessageProcessor.
     * @param serverCallStreamObserver
     */
    public FileKafkaEventMessageProcessor(StreamObservable<FileStreamEvent> serverCallStreamObserver) {
        int chunkSize = PropertyUtil.getPropertyIntValue(CHUNK_SIZE, DEFAULT_ONE_MB_SIZE);
        this.serverCallStreamObserver = Objects.requireNonNull(serverCallStreamObserver, "serverCallStreamObserver");
        this.fileChunkStreamer = new FileChunkStreamer(chunkSize);
        this.validator = new FileTransferRequestValidator();
    }

    private static String classify(Exception e) {
        if (e instanceof JsonProcessingException) {
            return "DESERIALIZATION";
        }
        if (e instanceof IllegalArgumentException) {
            return "VALIDATION";
        }
        if (e instanceof FileTransferException) {
            return "VALIDATION";
        }
        // Treat unexpected failures as validation/guard failures for downstream behaviour.
        return "VALIDATION";
    }

    /**
     * Processes the Kafka event by reading the file path from the event value and streaming
     * the file contents to the provided StreamObservable.
     * @param kafkaEvent
     */
    @Override
    public void process(KafkaEvent<String, byte[]> kafkaEvent) {
        long offset = kafkaEvent.getConsumerRecord().offset();
        LOGGER.info("Processing file sequence_id : {} ", offset);

        byte[] payload = kafkaEvent.value();

        try {
            FileTransferRequest fileTransferRequest =
                    ObjectMapperUtil.getInstance().readValue(payload, FileTransferRequest.class);

            validator.validate(fileTransferRequest);

            fileChunkStreamer.stream(offset, fileTransferRequest, serverCallStreamObserver);
            LOGGER.info("File sequence id : {} streamed path: {}", offset, fileTransferRequest.path());
        } catch (Exception e) {
            LOGGER.warn("Skipping file sequence id : {} due to {}", offset, classify(e), e);

            serverCallStreamObserver.onNext(FileStreamEvent.newBuilder()
                    .setWarning(StreamWarning.newBuilder()
                            .setSkippedSequenceId(offset)
                            .setReason(classify(e))
                            .setDetails(e.getMessage())
                            .build())
                    .build());
        }
    }
}
