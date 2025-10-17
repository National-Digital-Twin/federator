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

package uk.gov.dbt.ndtp.federator.grpc;

import io.grpc.*;
import io.grpc.Context.CancellableContext;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import lombok.SneakyThrows;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.exceptions.ClientGRPCJobException;
import uk.gov.dbt.ndtp.federator.exceptions.RetryableException;
import uk.gov.dbt.ndtp.federator.grpc.interceptor.AuthClientInterceptor;
import uk.gov.dbt.ndtp.federator.grpc.interceptor.CustomClientInterceptor;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.utils.SSLUtils;
import uk.gov.dbt.ndtp.grpc.*;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.sinks.KafkaSink;
import uk.gov.dbt.ndtp.secure.agent.sources.memory.SimpleEvent;

/**
 * GRPCClient is a client for the FederatorService GRPC service.
 * It is used to obtain topics and consume messages from the GRPC service.
 * It also sends messages to the KafkaSink.
 * It is also used to test connectivity to the KafkaSink.
 * It is also used to test connectivity to the GRPC service.
 * It is also used to close the GRPC client.
 * It is also used to process a topic.
 * It is also used to consume messages and send them to the KafkaSink.
 */
public class GRPCClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("GRPClient");

    private static final String CLIENT_KEEP_ALIVE_TIME = "client.keepAliveTime.secs";
    private static final String CLIENT_KEEP_ALIVE_TIMEOUT = "client.keepAliveTimeout.secs";
    private static final String CLIENT_IDLE_TIMEOUT = "client.idleTimeout.secs";
    private static final String CLIENT_P12_FILE_PATH = "client.p12FilePath";
    private static final String CLIENT_P12_PASSWORD = "client.p12Password";
    private static final String CLIENT_TRUSTSTORE_FILE_PATH = "client.truststoreFilePath";
    private static final String CLIENT_TRUSTSTORE_PASSWORD = "client.truststorePassword";
    private static final String TEN = "10";
    private static final String THIRTY = "30";
    private final ManagedChannel channel;
    private final FederatorServiceGrpc.FederatorServiceBlockingStub blockingStub;
    private final String client;
    private final String key;
    private final String topicPrefix;
    private final String serverName;

    public GRPCClient(ConnectionProperties connectionProperties, String topicPrefix) {
        this(
                connectionProperties.clientName(),
                connectionProperties.clientKey(),
                connectionProperties.serverName(),
                connectionProperties.serverHost(),
                connectionProperties.serverPort(),
                connectionProperties.tls(),
                topicPrefix);
    }

    public GRPCClient(
            String client,
            String key,
            String serverName,
            String host,
            int port,
            boolean isTLSEnabled,
            String topicPrefix) {
        LOGGER.info(
                "Initializing GRPCClient with client={}, serverName={}, host={}, port={}, isTLSEnabled={}, topicPrefix={}",
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
        if (isTLSEnabled) {
            LOGGER.info("Using TLS for GRPC connection");
            channel = generateSecureChannel(host, port, generateChannelCredentials());
        } else {
            LOGGER.info("Using plaintext for GRPC connection");
            channel = generateChannel(host, port);
        }

        blockingStub = FederatorServiceGrpc.newBlockingStub(channel);
    }

    public static void sendMessage(KafkaSink<Bytes, Bytes> sink, KafkaByteBatch batch) {
        LOGGER.debug("Creating message to send");
        Bytes key = new Bytes(batch.getKey().toByteArray());
        Bytes value = new Bytes(batch.getValue().toByteArray());
        List<Header> headers = batch.getSharedList().stream()
                .map(h -> new Header(h.getKey(), h.getValue()))
                .collect(Collectors.toList());
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

    public static ManagedChannel generateChannel(String host, int port) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        builder.usePlaintext();
        return configureChannelBuilder(builder).build();
    }

    public static ManagedChannel generateSecureChannel(String host, int port, ChannelCredentials cred) {
        ManagedChannelBuilder<?> builder = Grpc.newChannelBuilderForAddress(host, port, cred);
        return configureChannelBuilder(builder).build();
    }

    private static ManagedChannelBuilder<?> configureChannelBuilder(ManagedChannelBuilder<?> builder) {
        IdpTokenService tokenService = GRPCUtils.createIdpTokenService();
        return builder.keepAliveTime(PropertyUtil.getPropertyIntValue(CLIENT_KEEP_ALIVE_TIME, THIRTY), TimeUnit.SECONDS)
                .keepAliveTimeout(PropertyUtil.getPropertyIntValue(CLIENT_KEEP_ALIVE_TIMEOUT, TEN), TimeUnit.SECONDS)
                .idleTimeout(PropertyUtil.getPropertyIntValue(CLIENT_IDLE_TIMEOUT, TEN), TimeUnit.SECONDS)
                .intercept(new CustomClientInterceptor(), new AuthClientInterceptor(tokenService));
    }

    private KeyManager[] createKeyManagerFromP12() {
        String clientP12FilePath = PropertyUtil.getPropertyValue(CLIENT_P12_FILE_PATH);
        String password = PropertyUtil.getPropertyValue(CLIENT_P12_PASSWORD);
        // log info for filepath and boolean that password in not null
        LOGGER.info(
                "Creating KeyManager with clientP12FilePath: {}, password: {}",
                clientP12FilePath,
                password != null ? "******" : "null");

        return SSLUtils.createKeyManagerFromP12(clientP12FilePath, password);
    }

    /**
     * Create TrustManagerFactory from JKS file path
     */
    public TrustManager[] createTrustManager() {
        String trustStoreFilePath = PropertyUtil.getPropertyValue(CLIENT_TRUSTSTORE_FILE_PATH);
        String trustStorePassword = PropertyUtil.getPropertyValue(CLIENT_TRUSTSTORE_PASSWORD);

        LOGGER.info(
                "Creating TrustManager with trustStoreFilePath: {}, trustStorePassword: {}",
                trustStoreFilePath,
                trustStorePassword != null ? "******" : "null");
        return SSLUtils.createTrustManager(trustStoreFilePath, trustStorePassword);
    }

    @SneakyThrows
    private ChannelCredentials generateChannelCredentials() {

        return TlsChannelCredentials.newBuilder()
                .keyManager(createKeyManagerFromP12())
                .trustManager(createTrustManager())
                .build();
    }

    public String getRedisPrefix() {
        return client + "-" + serverName;
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (Throwable t) {
            LOGGER.error("Exception closing client", t);
        }
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
            LOGGER.warn("Failed to create KafkaSink - '{}'", e.getMessage());
            throw new RetryableException(e);
        }
    }

    public void consumeMessagesAndSendOn(TopicRequest req, KafkaSink<Bytes, Bytes> sink) {
        LOGGER.info("Consuming messages for topic: {}", req.getTopic());

        long idleSeconds = PropertyUtil.getPropertyIntValue(CLIENT_IDLE_TIMEOUT, TEN);

        ExecutorService threadExecutor = Executors.newSingleThreadExecutor();
        CancellableContext context = Context.current().withCancellation();

        try {
            Iterator<KafkaByteBatch> iterator = context.call(() -> blockingStub.getKafkaConsumer(req));

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

                LOGGER.debug(
                        "Consuming message: {}, {} : {}",
                        batch.getTopic(),
                        batch.getOffset(),
                        batch.getValue().toStringUtf8());
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
            context.cancel(null);
            threadExecutor.shutdownNow();
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
    private KafkaByteBatch getNextBatch(Future<KafkaByteBatch> futureNext, long idleSeconds, CancellableContext context)
            throws Exception {

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
        KafkaUtil.getKafkaSinkBuilder().topic("test").build().close();
    }
}
