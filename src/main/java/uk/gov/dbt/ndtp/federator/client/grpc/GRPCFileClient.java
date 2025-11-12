// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
package uk.gov.dbt.ndtp.federator.client.grpc;

import io.grpc.ManagedChannel;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.client.grpc.file.FileChunkAssembler;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.FileAssemblyException;
import uk.gov.dbt.ndtp.grpc.FederatorServiceGrpc;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;

/**
 * GRPCTopicClient is a client for the FederatorService GRPC service.
 * It is used to obtain topics and consume messages from the GRPC service.
 * It also sends messages to the KafkaSink.
 * It is also used to test connectivity to the KafkaSink.
 * It is also used to test connectivity to the GRPC service.
 * It is also used to close the GRPC client.
 * It is also used to process a topic.
 * It is also used to consume messages and send them to the KafkaSink.
 */
@Slf4j
public class GRPCFileClient implements GRPCClient {

    private final ManagedChannel channel;
    private final FederatorServiceGrpc.FederatorServiceBlockingStub blockingStub;
    private final String client;
    private final String key;
    private final String topicPrefix;
    private final String serverName;

    public GRPCFileClient(ConnectionProperties connectionProperties, String topicPrefix) {
        this(
                connectionProperties.clientName(),
                connectionProperties.clientKey(),
                connectionProperties.serverName(),
                connectionProperties.serverHost(),
                connectionProperties.serverPort(),
                connectionProperties.tls(),
                topicPrefix);
    }

    public GRPCFileClient(
            String client,
            String key,
            String serverName,
            String host,
            int port,
            boolean isTLSEnabled,
            String topicPrefix) {
        log.info(
                "Initializing GRPCFileClient with client={}, serverName={}, host={}, port={}, isTLSEnabled={}, topicPrefix={}",
                client,
                serverName,
                host,
                port,
                isTLSEnabled,
                topicPrefix);

        this.topicPrefix = topicPrefix;
        this.client = client;
        this.key = key;
        this.serverName = serverName;
        channel = generateChannel(host, port, isTLSEnabled);
        blockingStub = FederatorServiceGrpc.newBlockingStub(channel);
    }

    public String getRedisPrefix() {
        return getRedisPrefix(this.client, this.serverName);
    }

    public void processTopic(String topic, long offset) {
        LOGGER.info("Processing file topic: '{}' with start_sequence_id: '{}'", topic, offset);
        RedisUtil.getInstance();
        LOGGER.debug("Redis connectivity check passed");

        FileStreamRequest request = FileStreamRequest.newBuilder()
                .setTopic(topic)
                .setStartSequenceId(offset)
                .build();

        FileChunkAssembler assembler = new FileChunkAssembler();
        try {
            Iterator<FileChunk> chunks = blockingStub.getFilesStream(request);
            long lastSeq = offset;
            while (chunks.hasNext()) {
                FileChunk chunk = chunks.next();
                Path completed = assembler.accept(chunk);
                if (completed != null) {
                    long seqId = chunk.getFileSequenceId();
                    long next = seqId + 1;
                    RedisUtil.getInstance().setOffset(getRedisPrefix(), topic, next);
                    LOGGER.info(
                            "Completed file '{}' stored at {}. Saved next sequence id {} to Redis.",
                            chunk.getFileName(),
                            completed,
                            next);
                    lastSeq = seqId;
                }
            }
            LOGGER.info("Finished processing stream for topic '{}' at sequence id {}", topic, lastSeq);
        } catch (Exception e) {
            throw new FileAssemblyException("Unexpected error while processing file stream for topic " + topic, e);
        }
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // ✅ Preserve interrupt status
            log.warn("Client close interrupted", ie);
        } catch (Exception t) {
            log.error("Unexpected error while closing client", t);
        }
    }
}
