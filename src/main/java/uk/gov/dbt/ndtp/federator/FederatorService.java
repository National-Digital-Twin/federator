package uk.gov.dbt.ndtp.federator;

import java.util.Set;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.service.file.FileStreamService;
import uk.gov.dbt.ndtp.federator.common.service.kafka.KafkaStreamService;
import uk.gov.dbt.ndtp.federator.common.service.stream.FederatorStreamService;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileStreamEvent;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

/**
 * Federator service that provides methods to get Kafka consumers and file consumers.
 */
public class FederatorService {

    public static final Logger LOGGER = LoggerFactory.getLogger("FederatorService");
    private final FederatorStreamService<TopicRequest, KafkaByteBatch> kafkaStreamService;
    private final FederatorStreamService<FileStreamRequest, FileStreamEvent> fileStreamService;

    public FederatorService(Set<String> sharedHeaders) {
        this.kafkaStreamService = new KafkaStreamService(sharedHeaders);
        this.fileStreamService = new FileStreamService();
    }

    /**
     * Gets a Kafka consumer for the given topic request and streams data to the provided stream observable.
     * @param request
     * @param streamObservable
     * @throws InvalidTopicException
     */
    public void getKafkaConsumer(TopicRequest request, StreamObservable<KafkaByteBatch> streamObservable)
            throws InvalidTopicException {
        kafkaStreamService.streamToClient(request, streamObservable);
    }

    /**
     * Gets a file consumer for the given file stream request and streams data to the provided stream observable.
     * @param request
     * @param streamObservable
     */
    public void getFileConsumer(FileStreamRequest request, StreamObservable<FileStreamEvent> streamObservable) {
        fileStreamService.streamToClient(request, streamObservable);
    }
}
