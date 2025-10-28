package uk.gov.dbt.ndtp.federator.service.file;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.conductor.FileConductor;
import uk.gov.dbt.ndtp.federator.conductor.MessageConductor;
import uk.gov.dbt.ndtp.federator.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.service.stream.FederatorStreamService;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;

public class FileStreamService implements FederatorStreamService<FileStreamRequest, FileChunk> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStreamService.class);
    private static final ExecutorService THREADED_EXECUTOR = ThreadUtil.threadExecutor("FileStreamService");

    /**
     * Streams file chunks to the client based on the file request.
     * @param fileRequest
     * @param streamObservable
     */
    @Override
    public void streamToClient(FileStreamRequest fileRequest, StreamObservable<FileChunk> streamObservable) {

        long offset = fileRequest.getStartSequenceId();
        String consumerId = GRPCContextKeys.CLIENT_ID.get();
        streamObservable.setOnCancelHandler(() -> LOGGER.info("Cancel called by client: {}", consumerId));

        ClientTopicOffsets topicData = new ClientTopicOffsets(consumerId, fileRequest.getTopic(), offset);
        MessageConductor messageConductor = new FileConductor(topicData, streamObservable);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(THREADED_EXECUTOR.submit(messageConductor::processMessages));
        LOGGER.info(
                "Awaiting FileStreamRequest finished for Client: {}, Topic: {}, Offset: {}",
                consumerId,
                topicData.getTopic(),
                topicData.getOffset());
        ThreadUtil.awaitShutdown(futures, messageConductor, THREADED_EXECUTOR);
        LOGGER.info(
                "Finished FileStreamRequest processed for Client: {}, Topic: {}, Offset: {}",
                consumerId,
                topicData.getTopic(),
                topicData.getOffset());
        streamObservable.onCompleted();
    }
}
