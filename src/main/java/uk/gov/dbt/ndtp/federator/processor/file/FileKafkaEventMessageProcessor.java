package uk.gov.dbt.ndtp.federator.processor.file;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.model.FileFetchRequest;
import uk.gov.dbt.ndtp.federator.processor.MessageProcessor;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Reads a file path from the Kafka event value and streams the file contents as one or more
 * {@link KafkaByteBatch} messages to the provided {@link StreamObservable}.
 * <p>
 * The processor reads files in configurable chunk sizes to avoid loading entire files into memory.
 */
public class FileKafkaEventMessageProcessor implements MessageProcessor<KafkaEvent<String, FileFetchRequest>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("FileKafkaEventMessageProcessor");
    private static final String CHUNK_SIZE = "file.stream.chunk.size";
    private final StreamObservable<FileChunk> serverCallStreamObserver;
    private final FileChunkStreamer fileChunkStreamer;

    /**
     * Create a processor with optional base directory and chunk size.
     */
    public FileKafkaEventMessageProcessor(StreamObservable<FileChunk> serverCallStreamObserver) {
        int chunkSize = PropertyUtil.getPropertyIntValue(CHUNK_SIZE, "1000");
        this.serverCallStreamObserver = Objects.requireNonNull(serverCallStreamObserver, "serverCallStreamObserver");
        this.fileChunkStreamer = new FileChunkStreamer(chunkSize);
    }

    @Override
    public void process(KafkaEvent<String, FileFetchRequest> kafkaEvent) {
        LOGGER.info(
                "Processing file sequence_id : {} ",
                kafkaEvent.getConsumerRecord().offset());
        FileFetchRequest filePath = kafkaEvent.value();
        fileChunkStreamer.stream(kafkaEvent.getConsumerRecord().offset(), filePath, serverCallStreamObserver);
        LOGGER.info("File sequence id : {} ", filePath);
    }
}
