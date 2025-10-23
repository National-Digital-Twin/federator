package uk.gov.dbt.ndtp.federator;

import java.util.Set;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.service.FederatorStreamService;
import uk.gov.dbt.ndtp.federator.service.FileStreamService;
import uk.gov.dbt.ndtp.federator.service.KafkaStreamService;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

public class FederatorService {

    public static final Logger LOGGER = LoggerFactory.getLogger("FederatorService");
    private final FederatorStreamService<TopicRequest, KafkaByteBatch> kafkaStreamService;
    private final FederatorStreamService<FileStreamRequest, FileChunk> fileStreamService;

    public FederatorService(Set<String> sharedHeaders) {
        this.kafkaStreamService = new KafkaStreamService(sharedHeaders);
        this.fileStreamService = new FileStreamService();
    }

    public void getKafkaConsumer(TopicRequest request, StreamObservable<KafkaByteBatch> streamObservable)
            throws InvalidTopicException {
        kafkaStreamService.streamToClient(request, streamObservable);
    }

    public void getFileConsumer(FileStreamRequest request, StreamObservable<FileChunk> streamObservable) {
        fileStreamService.streamToClient(request, streamObservable);
    }
}
