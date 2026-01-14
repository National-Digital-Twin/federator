// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.client.grpc;

import io.grpc.Context;
import io.grpc.Context.CancellableContext;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.common.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ClientGRPCJobException;
import uk.gov.dbt.ndtp.federator.exceptions.RetryableException;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.grpc.TopicRequest;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.sinks.KafkaSink;
import uk.gov.dbt.ndtp.secure.agent.sources.memory.SimpleEvent;

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
@SuppressWarnings("all")
public class GRPCTopicClient extends GRPCAbstractClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("GRPClient");
    private static final String CLIENT_IDLE_TIMEOUT = "client.idleTimeout.secs";

    public GRPCTopicClient(ConnectionProperties connectionProperties, String topicPrefix) {
        this(
                connectionProperties.clientName(),
                connectionProperties.clientKey(),
                connectionProperties.serverName(),
                connectionProperties.serverHost(),
                connectionProperties.serverPort(),
                connectionProperties.tls(),
                topicPrefix);
    }

    public GRPCTopicClient(
            String client,
            String key,
            String serverName,
            String host,
            int port,
            boolean isTLSEnabled,
            String topicPrefix) {
        super(client, key, serverName, host, port, isTLSEnabled, topicPrefix);
        LOGGER.info(
                "Initializing GRPCTopicClient with client={}, serverName={}, host={}, port={}, isTLSEnabled={}, topicPrefix={}",
                client,
                serverName,
                host,
                port,
                isTLSEnabled,
                topicPrefix);
    }

    /**
     * Protected pass-through constructor to support tests that need to inject a mock channel.
     */
    protected GRPCTopicClient(
            String client, String key, String serverName, String topicPrefix, ManagedChannel channel) {
        super(client, key, serverName, topicPrefix, channel);
        LOGGER.info(
                "Initializing GRPCTopicClient (injected channel) with client={}, serverName={}, topicPrefix={}",
                client,
                serverName,
                topicPrefix);
    }

    public static void sendMessage(KafkaSink<Bytes, Bytes> sink, KafkaByteBatch batch) {
        LOGGER.debug("Creating message to send");
        Bytes key = new Bytes(batch.getKey().toByteArray());
        Bytes value = new Bytes(batch.getValue().toByteArray());
        List<Header> headers = batch.getSharedList().stream()
                .map(h -> new Header(h.getKey(), h.getValue()))
                .toList();
        Event<Bytes, Bytes> event = new SimpleEvent<>(headers, key, value);
        LOGGER.debug("Sending message");
        sink.send(event);
        LOGGER.debug("Sent event");
    }

    public static KafkaSink<Bytes, Bytes> getSender(String topic, String topicPrefix, String serverName) {
        return KafkaUtil.getKafkaSink(concatCompoundTopicName(topic, topicPrefix, serverName));
    }

    private static String concatCompoundTopicName(String topic, String topicPrefix, String serverName) {
        if (topicPrefix.isEmpty()) {
            return String.join("-", serverName, topic);
        }
        return String.join("-", topicPrefix, serverName, topic);
    }

    public void processTopic(String topic, long offset) {
        LOGGER.info("Processing topic: '{}' with offset: '{}'", topic, offset);
        RedisUtil.getInstance();
        LOGGER.debug("Redis connectivity check passed");
        TopicRequest topicRequest =
                TopicRequest.newBuilder().setTopic(topic).setOffset(offset).build();

        try (KafkaSink<Bytes, Bytes> sink = getSender(topic, this.topicPrefix, this.serverName)) {
            LOGGER.debug("Kafka sink created successfully");
            try (Context.CancellableContext withCancellation = Context.current().withCancellation()) {
                withCancellation.run(() -> consumeMessagesAndSendOn(topicRequest, sink));
                LOGGER.info("Topic {} processed", topic);
            } catch (StatusRuntimeException exception) {
                if (Status.INVALID_ARGUMENT
                        .getCode()
                        .equals(exception.getStatus().getCode())) {
                    LOGGER.error("Topic ({}) no longer valid for client ({})", topic, client);
                } else {
                    LOGGER.error("Topic processing stopped due to unknown error.", exception);
                }
                throw exception;
            }
        } catch (KafkaException e) {
            throw new RetryableException(e);
        }
    }

    public void consumeMessagesAndSendOn(TopicRequest req, KafkaSink<Bytes, Bytes> sink) {
        LOGGER.info("Consuming messages for topic: {}", req.getTopic());

        long idleSeconds = PropertyUtil.getPropertyIntValue(CLIENT_IDLE_TIMEOUT, TEN);

        ExecutorService threadExecutor = null;
        CancellableContext context = null;
        try {
            threadExecutor = Executors.newSingleThreadExecutor();
            context = Context.current().withCancellation();
            Iterator<KafkaByteBatch> iterator = context.call(() -> getStub().getKafkaConsumer(req));

            while (true) {
                // Note that with the source being a blocking iterator, the call to next() will block until a message is
                // available.
                // To avoid blocking indefinitely (as idle timeouts will not be reached), we use a Future with a timeout
                // to limit how long we wait for a message.
                Future<KafkaByteBatch> futureNext = threadExecutor.submit(context.wrap(iterator::next));
                KafkaByteBatch batch = getNextBatch(futureNext, idleSeconds, context);
                if (batch == null) {
                    break;
                }

                LOGGER.debug("Consuming message: {}, {} : {}", batch.getTopic(), batch.getOffset(), batch.getValue());
                sendMessage(sink, batch);

                // The persisted offset here is read when a new job starts.
                // Store the next offset to be read to avoid record overlaps.
                long nextOffset = batch.getOffset() + 1;
                RedisUtil.getInstance().setOffset(getRedisPrefix(), req.getTopic(), nextOffset);
                LOGGER.debug("Wrote next offset {} to redis for topic {}", nextOffset, req.getTopic());
            }
        } catch (Exception e) {
            throw new ClientGRPCJobException("Error encountered whilst consuming topic", e);
        } finally {
            if (context != null) {
                context.cancel(null);
            }
            if (threadExecutor != null) {
                threadExecutor.shutdownNow();
                try {
                    if (!threadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOGGER.warn("Thread executor did not terminate in time");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            LOGGER.info("Finished consuming topic");
        }
    }

    /***
     * Gets the next batch from the future, with a timeout to avoid blocking indefinitely.
     * @param futureNext The future to get the next batch from.
     * @param idleSeconds The number of seconds to wait before timing out.
     * @param context The cancellable context to cancel if the timeout is reached.
     * @return The next batch, or null if the timeout is reached.
     * @throws Exception If an error occurs while getting the next batch.
     */
    @SneakyThrows
    private KafkaByteBatch getNextBatch(
            Future<KafkaByteBatch> futureNext, long idleSeconds, CancellableContext context) {

        // To avoid blocking indefinitely (as idle timeouts will not be reached),
        // use a Future with a timeout to limit how long we wait for a message.
        try {
            return futureNext.get(idleSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            context.cancel(null);
            LOGGER.info("No messages received for {}s. Closing consumer.", idleSeconds);
            return null;
        } catch (ExecutionException ee) {
            Throwable c = ee.getCause();
            if (c instanceof StatusRuntimeException sre) {
                Status.Code code = sre.getStatus().getCode();
                // Server closed or call cancelled/deadline: treat as end of stream
                if (code == Status.Code.OUT_OF_RANGE
                        || code == Status.Code.CANCELLED
                        || code == Status.Code.DEADLINE_EXCEEDED) {
                    LOGGER.info("Stream ended: {}", code);
                    return null;
                }
            }
            throw ee;
        }
    }

    public void testConnectivity() {
        // getStub().testConnectivity(TopicRequest.getDefaultInstance());
    }
}
