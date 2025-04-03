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
package uk.gov.dbt.ndtp.federator;

import static uk.gov.dbt.ndtp.federator.utils.StringUtils.throwIfBlank;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.client.connection.ConfigurationException;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionsProperties;
import uk.gov.dbt.ndtp.federator.grpc.GRPCClient;
import uk.gov.dbt.ndtp.federator.lifecycle.AutoClosableShutdownTask;
import uk.gov.dbt.ndtp.federator.lifecycle.CancelableFutureShutdownTask;
import uk.gov.dbt.ndtp.federator.lifecycle.ExecutorShutdownTask;
import uk.gov.dbt.ndtp.federator.lifecycle.ProgressReporter;
import uk.gov.dbt.ndtp.federator.lifecycle.ShutdownTask;
import uk.gov.dbt.ndtp.federator.lifecycle.ShutdownThread;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;

/**
 * Main class for the Federator client.
 * <p>
 *   The Federator client is responsible for connecting to the Federator server and consuming messages from the Kafka
 *   topic. The client is configured with a set of connection properties that define the server to connect to and the Kafka topic to consume from.
 * </p>
 */
public class FederatorClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("FederatorClient");
    private static final ExecutorService CLIENT_POOL = ThreadUtil.threadExecutor("Clients");
    // config properties
    private static final String FEDERATOR_CLIENT_PROPERTIES = "FEDERATOR_CLIENT_PROPERTIES";
    private static final String CLIENT_PROPERTIES = "client.properties";
    private static final String CONNECTIONS_PROPERTIES = "connections.configuration";
    private static final String KAFKA_TOPIC_PREFIX = "kafka.topic.prefix";

    static {
        String clientProperties = System.getenv(FEDERATOR_CLIENT_PROPERTIES);
        if (null != clientProperties) {
            File f = new File(clientProperties);
            PropertyUtil.init(f);
        } else {
            PropertyUtil.init(CLIENT_PROPERTIES);
        }
    }

    private final GRPCClientBuilder clientBuilder;

    FederatorClient(GRPCClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    public static void main(String[] args) {
        final String prefix = PropertyUtil.getPropertyValue(KAFKA_TOPIC_PREFIX, "");

        new FederatorClient(config -> new GRPCClient(config, prefix)).run();
    }

    void run() {
        try {
            initialiseConnectionProperties();
        } catch (Exception e) {
            LOGGER.error(
                    "Key properties not set correctly. Federator client needs to stop. Reason: {}", e.getMessage());
            System.exit(1);
        }
        List<ConnectionProperties> configs = ConnectionsProperties.get().config();
        ShutdownThread.register(new ExecutorShutdownTask(CLIENT_POOL));

        ClientMonitor monitor = new ClientMonitor(configs.size());
        ShutdownThread.register(monitor::close);

        for (ConnectionProperties config : configs) {
            Future<?> task = CLIENT_POOL.submit(() -> {
                ShutdownTask clientCloser = null;
                try (GRPCClient client = clientBuilder.build(config);
                        WrappedGRPCClient grpcClient = new WrappedGRPCClient(client)) {

                    clientCloser = new AutoClosableShutdownTask(grpcClient);
                    ShutdownThread.register(clientCloser);

                    KafkaRunner runner = new KafkaRunner(monitor);
                    runner.run(grpcClient);
                } catch (Exception e) {
                    LOGGER.error(
                            "Exception encountered while creating GRPCClient for {} as {}. Shutting down client. Reason - {}",
                            config.serverHost(),
                            config.clientName(),
                            e.getMessage());
                    monitor.registerComplete();
                } finally {
                    if (clientCloser != null) {
                        ShutdownThread.unregister(clientCloser);
                    }
                }
            });
            ShutdownThread.register(new CancelableFutureShutdownTask(task));
        }

        monitor.awaitCompletion();

        LOGGER.info("All clients stopped");
    }

    private static void initialiseConnectionProperties() {
        String location = PropertyUtil.getPropertyValue(CONNECTIONS_PROPERTIES);

        throwIfBlank(
                location,
                () -> new ConfigurationException("'%s' needs to be set in config".formatted(CONNECTIONS_PROPERTIES)));

        ConnectionsProperties properties;
        try {
            var file = new File(location);
            properties = ConnectionsProperties.init(file);
        } catch (Exception e) {
            throw new ConfigurationException(
                    "Could not read the connection configuration defined in %s due to %s"
                            .formatted(location, e.getMessage()),
                    e);
        }
        if (properties.config().isEmpty()) {
            throw new ConfigurationException(
                    "No connection are found to be configured, at least one connection configuration is required");
        }
    }

    interface GRPCClientBuilder {
        GRPCClient build(ConnectionProperties config);
    }

    static class ClientMonitor implements ProgressReporter {

        private static final Logger log = LoggerFactory.getLogger("TaskMonitor");

        private final AtomicInteger remaining;
        private final CountDownLatch latch;

        ClientMonitor(int numClients) {
            this.latch = new CountDownLatch(numClients);
            this.remaining = new AtomicInteger(numClients);
        }

        void awaitCompletion() {
            try {
                latch.await();
            } catch (InterruptedException ignore) {
            }
        }

        void close() {
            // complete the latch, if some other tasks register as complete it should have no impact as the latch stays
            // at 0 once decremented to it
            while (remaining.get() > 0) {
                registerComplete();
            }
        }

        @Override
        public void registerComplete() {
            latch.countDown();
            int left = remaining.decrementAndGet();
            log.debug("Client complete, {} left", left);
        }
    }
}
