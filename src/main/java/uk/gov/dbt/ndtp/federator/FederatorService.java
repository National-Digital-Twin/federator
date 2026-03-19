package uk.gov.dbt.ndtp.federator;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.service.file.FileStreamService;
import uk.gov.dbt.ndtp.federator.common.service.kafka.KafkaStreamService;
import uk.gov.dbt.ndtp.federator.common.service.stream.CloseableFederatorStreamService;
import uk.gov.dbt.ndtp.federator.common.utils.ThreadUtil;
import uk.gov.dbt.ndtp.federator.server.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.FileStreamEvent;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

/**
 * Federator service that provides methods to get Kafka consumers and file consumers.
 */
public class FederatorService implements AutoCloseable {

    public static final Logger LOGGER = LoggerFactory.getLogger("FederatorService");
    private static final ExecutorService THREADED_FILE_STREAM_SERVICE_EXECUTOR =
            ThreadUtil.threadExecutor("FileStreamService");
    private static final ExecutorService THREADED_KAFKA_STREAM_SERVICE_EXECUTOR =
            ThreadUtil.threadExecutor("KafkaStreamService");
    private final CloseableFederatorStreamService<TopicRequest, KafkaByteBatch> kafkaStreamService;
    private final CloseableFederatorStreamService<FileStreamRequest, FileStreamEvent> fileStreamService;

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
        kafkaStreamService.streamToClient(request, streamObservable, THREADED_KAFKA_STREAM_SERVICE_EXECUTOR);
    }

    /**
     * Gets a file consumer for the given file stream request and streams data to the provided stream observable.
     * @param request
     * @param streamObservable
     */
    public void getFileConsumer(FileStreamRequest request, StreamObservable<FileStreamEvent> streamObservable) {
        fileStreamService.streamToClient(request, streamObservable, THREADED_FILE_STREAM_SERVICE_EXECUTOR);
    }

    @Override
    public void close() {
        fileStreamService.close();
        kafkaStreamService.close();
        THREADED_FILE_STREAM_SERVICE_EXECUTOR.shutdown();
        THREADED_KAFKA_STREAM_SERVICE_EXECUTOR.shutdown();
    }
}
