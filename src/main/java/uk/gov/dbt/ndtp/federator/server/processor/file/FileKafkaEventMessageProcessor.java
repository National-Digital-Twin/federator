package uk.gov.dbt.ndtp.federator.server.processor.file;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Reads a file path from the Kafka event value and streams the file contents as one or more
 * {@link KafkaByteBatch} messages to the provided {@link StreamObservable}.
 * <p>
 * The processor reads files in configurable chunk sizes to avoid loading entire files into memory.
 */
public class FileKafkaEventMessageProcessor implements MessageProcessor<KafkaEvent<String, FileTransferRequest>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("FileKafkaEventMessageProcessor");
    private static final String CHUNK_SIZE = "file.stream.chunk.size";
    private final StreamObservable<FileChunk> serverCallStreamObserver;
    private final FileChunkStreamer fileChunkStreamer;

    /**
     * Constructor for FileKafkaEventMessageProcessor.
     * @param serverCallStreamObserver
     */
    public FileKafkaEventMessageProcessor(StreamObservable<FileChunk> serverCallStreamObserver) {
        int chunkSize = PropertyUtil.getPropertyIntValue(CHUNK_SIZE, "1000");
        this.serverCallStreamObserver = Objects.requireNonNull(serverCallStreamObserver, "serverCallStreamObserver");
        this.fileChunkStreamer = new FileChunkStreamer(chunkSize);
    }

    /**
     * Processes the Kafka event by reading the file path from the event value and streaming
     * the file contents to the provided StreamObservable.
     * @param kafkaEvent
     */
    @Override
    public void process(KafkaEvent<String, FileTransferRequest> kafkaEvent) {
        LOGGER.info(
                "Processing file sequence_id : {} ",
                kafkaEvent.getConsumerRecord().offset());
        FileTransferRequest filePath = kafkaEvent.value();
        fileChunkStreamer.stream(kafkaEvent.getConsumerRecord().offset(), filePath, serverCallStreamObserver);
        LOGGER.info("File sequence id : {} ", filePath);
    }
}
