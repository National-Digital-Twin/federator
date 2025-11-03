package uk.gov.dbt.ndtp.federator.server.conductor;

import java.util.Collections;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.model.FileTransferRequest;
import uk.gov.dbt.ndtp.federator.server.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.server.consumer.KafkaEventMessageConsumer;
import uk.gov.dbt.ndtp.federator.server.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.federator.server.processor.file.FileKafkaEventMessageProcessor;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Reads a file path from the Kafka event value (plain string),
 * streams the file contents into a KafkaByteBatch and forwards it to the server call stream observer.
 */
public class FileConductor extends AbstractKafkaEventMessageConductor<String, FileTransferRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger("FileConductor");

    private final StreamObservable<FileChunk> serverCallStreamObserver;

    public FileConductor(ClientTopicOffsets topicData, StreamObservable<FileChunk> serverCallStreamObserver) {

        this(
                serverCallStreamObserver,
                new KafkaEventMessageConsumer<>(
                        StringDeserializer.class,
                        FileFetchRequestJsonDeserializer.class,
                        topicData.getTopic(),
                        topicData.getOffset(),
                        topicData.getClient()),
                new FileKafkaEventMessageProcessor(serverCallStreamObserver));
    }

    private FileConductor(
            StreamObservable<FileChunk> serverCallStreamObserver,
            MessageConsumer<KafkaEvent<String, FileTransferRequest>> consumer,
            MessageProcessor<KafkaEvent<String, FileTransferRequest>> postProcessor) {

        super(consumer, postProcessor, Collections.emptyList());
        this.serverCallStreamObserver = serverCallStreamObserver;
    }

    @Override
    public boolean continueProcessing() {
        if (serverCallStreamObserver.isCancelled()) {
            LOGGER.info("Observer is closed on client end. Stop further processing.");
            messageConsumer.close();
            return false;
        }
        return messageConsumer.stillAvailable();
    }
}
