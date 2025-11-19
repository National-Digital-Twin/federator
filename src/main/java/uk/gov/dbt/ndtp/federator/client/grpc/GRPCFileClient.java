// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
package uk.gov.dbt.ndtp.federator.client.grpc;

import java.nio.file.Path;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.client.grpc.file.FileChunkAssembler;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.FileAssemblyException;
import uk.gov.dbt.ndtp.grpc.FileChunk;
import uk.gov.dbt.ndtp.grpc.FileStreamRequest;

/**
 * GRPC client responsible for streaming files from the Federator service and assembling them locally.
 *
 * <p>Uses {@link uk.gov.dbt.ndtp.federator.client.grpc.file.FileChunkAssembler} to assemble files from
 * streamed {@link uk.gov.dbt.ndtp.grpc.FileChunk} messages. When a file is completed, the configured
 * storage provider is invoked (LOCAL or S3).
 */
@Slf4j
public class GRPCFileClient extends GRPCAbstractClient {

    /**
     * Creates a client using the provided {@link ConnectionProperties} and topic prefix.
     *
     * @param connectionProperties gRPC and TLS connection parameters
     * @param topicPrefix prefix used when composing topic names
     */
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

    /**
     * Creates a client with explicit connection parameters.
     *
     * @param client logical client name used for Redis keys and logs
     * @param key client authentication key
     * @param serverName server logical name used for namespacing
     * @param host target host for the Federator gRPC service
     * @param port target port for the Federator gRPC service
     * @param isTLSEnabled whether to use TLS when connecting
     * @param topicPrefix prefix used when composing topic names
     */
    public GRPCFileClient(
            String client,
            String key,
            String serverName,
            String host,
            int port,
            boolean isTLSEnabled,
            String topicPrefix) {
        super(client, key, serverName, host, port, isTLSEnabled, topicPrefix);
        log.info(
                "Initializing GRPCFileClient with client={}, serverName={}, host={}, port={}, isTLSEnabled={}, topicPrefix={}",
                client,
                serverName,
                host,
                port,
                isTLSEnabled,
                topicPrefix);
    }

    /**
     * Streams files for the given topic starting at the provided sequence id and stores them via
     * {@link uk.gov.dbt.ndtp.federator.client.grpc.file.FileChunkAssembler} and the configured storage provider.
     * When each file completes, the next sequence id is persisted to Redis.
     *
     * @param topic topic name to stream
     * @param offset sequence id to begin from
     * @throws FileAssemblyException if streaming or assembly encounters an unrecoverable error
     */
    public void processTopic(String topic, long offset, String destination) {
        log.info("Processing file topic: '{}' with start_sequence_id: '{}'", topic, offset);
        RedisUtil.getInstance();
        log.debug("Redis connectivity check passed");

        FileStreamRequest request = FileStreamRequest.newBuilder()
                .setTopic(topic)
                .setStartSequenceId(offset)
                .build();

        // Pass destination to assembler so storage (LOCAL or S3) knows where to store the file
        FileChunkAssembler assembler = new FileChunkAssembler(destination);
        try {
            Iterator<FileChunk> chunks = getStub().getFilesStream(request);
            long lastSeq = offset;
            while (chunks.hasNext()) {
                FileChunk chunk = chunks.next();
                Path completed = assembler.accept(chunk);
                if (completed != null) {
                    long seqId = chunk.getFileSequenceId();
                    long next = seqId + 1;
                    RedisUtil.getInstance().setOffset(getRedisPrefix(), topic, next);
                    log.info(
                            "Completed file '{}' stored at {}. Saved next sequence id {} to Redis.",
                            chunk.getFileName(),
                            completed,
                            next);
                    lastSeq = seqId;
                }
            }
            log.info("Finished processing stream for topic '{}' at sequence id {}", topic, lastSeq);
        } catch (Exception e) {
            throw new FileAssemblyException("Unexpected error while processing file stream for topic " + topic, e);
        }
    }

    /**
     * Backward-compatible overload without destination; defaults to provider configuration.
     */
    public void processTopic(String topic, long offset) {
        processTopic(topic, offset, null);
    }
}
