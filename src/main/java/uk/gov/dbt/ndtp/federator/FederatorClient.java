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
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.client.connection.ConfigurationException;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionsProperties;
import uk.gov.dbt.ndtp.federator.grpc.GRPCClient;
import uk.gov.dbt.ndtp.federator.jobs.DefaultJobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.jobs.handlers.ClientDynamicConfigJob;
import uk.gov.dbt.ndtp.federator.jobs.params.JobParams;
import uk.gov.dbt.ndtp.federator.lifecycle.*;
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

    /**
     * Creates a new FederatorClient wired with a strategy to build GRPCClient instances per
     * connection configuration.
     *
     * @param clientBuilder factory used to create GRPC clients for configured connections
     */
    FederatorClient(GRPCClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    /**
     * Entry point for launching the Federator client.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        final String prefix = PropertyUtil.getPropertyValue(KAFKA_TOPIC_PREFIX, "");

        new FederatorClient(config -> new GRPCClient(config, prefix)).run();
    }

    /**
     * Validates and initialises the Federator connection properties from the configured location.
     * <p>
     * Ensures the configuration file is present, readable and contains at least one connection.
     * Throws a ConfigurationException with a useful message if validation fails.
     * </p>
     */
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

    /**
     * Executes the client lifecycle:
     * <ul>
     *   <li>Ensures the background job scheduler is started</li>
     *   <li>Registers the dynamic configuration job to pull client configs</li>
     *   <li>Validates connection properties and exits on failure</li>
     * </ul>
     */
    void run() {
        // Ensure Job Scheduler is started early in the lifecycle
        DefaultJobSchedulerProvider.getInstance().ensureStarted();

        JobParams jobParam = JobParams.builder()
                .jobId(UUID.randomUUID().toString())
                .AmountOfRetries(5)
                .duration(Duration.ofSeconds(30))
                .requireImmediateTrigger(true)
                .jobName("DynamicConfigProvider")
                .build();

        DefaultJobSchedulerProvider.getInstance().registerJob(new ClientDynamicConfigJob(), jobParam);
        try {
            initialiseConnectionProperties();
        } catch (Exception e) {
            LOGGER.error(
                    "Key properties not set correctly. Federator client needs to stop. Reason: {}", e.getMessage());
            System.exit(1);
        }

        LOGGER.info("All clients stopped");
    }

    /**
     * Factory for creating GRPCClient instances per connection configuration.
     */
    interface GRPCClientBuilder {
        /**
         * Builds a GRPCClient for the given connection configuration.
         *
         * @param config resolved connection properties for a single client/server pair
         * @return a new GRPCClient instance
         */
        GRPCClient build(ConnectionProperties config);
    }

    /**
     * Utility to coordinate completion of client tasks using a CountDownLatch.
     * Not thread-safe for re-use across different batches; intended per-run.
     */
    static class ClientMonitor implements ProgressReporter {

        private static final Logger log = LoggerFactory.getLogger("TaskMonitor");

        private final AtomicInteger remaining;
        private final CountDownLatch latch;

        /**
         * Creates a new monitor for the given number of client tasks.
         *
         * @param numClients number of clients to wait for
         */
        ClientMonitor(int numClients) {
            this.latch = new CountDownLatch(numClients);
            this.remaining = new AtomicInteger(numClients);
        }

        /**
         * Blocks until all registered clients have completed.
         */
        void awaitCompletion() {
            try {
                latch.await();
            } catch (InterruptedException ignore) {
            }
        }

        /**
         * Forces completion by draining the latch regardless of remaining tasks.
         * Safe to call multiple times; once at zero the latch remains open.
         */
        void close() {
            // complete the latch, if some other tasks register as complete it should have no impact as the latch stays
            // at 0 once decremented to it
            while (remaining.get() > 0) {
                registerComplete();
            }
        }

        /** {@inheritDoc} */
        @Override
        public void registerComplete() {
            latch.countDown();
            int left = remaining.decrementAndGet();
            log.debug("Client complete, {} left", left);
        }
    }
}
