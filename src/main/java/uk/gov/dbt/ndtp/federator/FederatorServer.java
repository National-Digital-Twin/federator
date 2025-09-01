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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.access.AccessMap;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.federator.filter.NoFilter;
import uk.gov.dbt.ndtp.federator.grpc.GRPCServer;
import uk.gov.dbt.ndtp.federator.utils.ClientFilter;
import uk.gov.dbt.ndtp.federator.utils.FilterReflectiveCreator;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.federator.jobs.DefaultJobSchedulerProvider;

/**
 * Main class for the Federator Server.
 * <p>
 * The Federator Server is a GRPC server that listens for incoming messages from the Federator Client.
 * The server will then filter the messages based on the client's access rights and return the filtered
 * messages to the client.
 * <p>
 * The server is configured using the following environment variables:
 * <ul>
 *     <li>FEDERATOR_SERVER_PROPERTIES - The location of the server.properties file</li>
 * </ul>
 * <p>
 * The server.properties file should contain the following properties:
 * <ul>
 *     <li>server.accessMapValueFile - The location of the access map file</li>
 *     <li>client.name - The name of the client</li>
 *     <li>filter.shareAll - Whether to share all messages or filter them</li>
 *     <li>shared.headers - The headers to share</li>
 * </ul>
 */
public class FederatorServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("FederatorServer");
    private static final String FEDERATOR_SERVER_PROPERTIES = "FEDERATOR_SERVER_PROPERTIES";
    private static final String SERVER_PROPERTIES = "server.properties";
    private static final String CLIENT_NAME = "client.name";
    private static final String FILTER_SHARE_ALL = "filter.shareAll";
    private static final String SERVER_ACCESS_MAP_VALUE_FILE = "server.accessMapValueFile";
    private static final String SHARED_HEADERS = "shared.headers";
    private static final String SHARED_HEADER_SEPARATOR = "\\^";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FALSE = "false";
    private static final String DEFAULT_FILTER_CLASSNAME =
            "uk.gov.dbt.ndtp.federator.filter.KafkaEventHeaderAttributeAccessFilter";
    private static final ExecutorService THREADED_EXECUTOR = ThreadUtil.threadExecutor("Server");

    static {
        String serverProperties = System.getenv(FEDERATOR_SERVER_PROPERTIES);
        if (null != serverProperties) {
            File f = new File(serverProperties);
            PropertyUtil.init(f);
        } else {
            PropertyUtil.init(SERVER_PROPERTIES);
        }
    }

    /**
     * Get the client from the access map.
     * <p>
     * If the client is not set or is not in the access map then the server will shut down.
     * @param client The client name
     * @return The client name
     */
    private static Optional<String> getValidClient(String client) {
        if (null == client || client.isEmpty()) {
            LOGGER.error("Shutting down as Federator Server needs '{}' set in config", CLIENT_NAME);
            return Optional.empty();
        } else if (null == AccessMap.get().getDetails(client)) {
            LOGGER.error(
                    "Shutting down as Federator Server client '{}' (set by '{}' in config) cannot be found in AccessMap",
                    client,
                    CLIENT_NAME);
            LOGGER.error(
                    "Likely that '{}' is not the key in the '{}' json file", CLIENT_NAME, SERVER_ACCESS_MAP_VALUE_FILE);
            return Optional.empty();
        }
        return Optional.of(client);
    }

    /**
     * Main method for the Federator Server.
     * <p>
     * The main method will initialise the access map and create the filters for the clients.
     * The server will then start the GRPC server
     * @param args The command line arguments
     * @throws IOException If there is an error reading the access map file
     */
    @SuppressWarnings("java:S1452") // SonarQube rule: Remove usage of generic wildcard type.
    public static void main(String[] args) throws IOException {

        // Ensure Job Scheduler is started early in the lifecycle
        DefaultJobSchedulerProvider.getInstance().ensureStarted();

        // Create the access map needed for AuthN for GRPC calls with the client key and
        // AuthZ if filtering
        String accessMapValueFile = PropertyUtil.getPropertyValue(SERVER_ACCESS_MAP_VALUE_FILE);
        if (null != accessMapValueFile && !accessMapValueFile.isEmpty()) {
            LOGGER.info("Initialising access map from file: {}", accessMapValueFile);
            File f = new File(accessMapValueFile);
            AccessMap.initFromFile(f);
        } else {
            LOGGER.error("'{}' that points to the client credentials is missing", SERVER_ACCESS_MAP_VALUE_FILE);
            System.exit(1);
        }
        boolean nofilter = PropertyUtil.getPropertyBooleanValue(FILTER_SHARE_ALL, FALSE);
        List<ClientFilter> clientFilters = new ArrayList<>();
        List<String> clientNames = AccessMap.get().getClientNames();
        List<String> validatedClients = new ArrayList<>();
        for (String clientName : clientNames) {
            Optional<String> client = getValidClient(clientName);
            if (client.isPresent()) {
                validatedClients.add(client.get());
            } else {
                LOGGER.error("Cannot get the client value for {}. Stopping as we cannot create a filter.", clientName);
                System.exit(1);
            }
        }

        if (nofilter) {
            LOGGER.info("Running using NoFilter so all data is shared ({}=true)", FILTER_SHARE_ALL);
            validatedClients.forEach(realClient -> clientFilters.add(new ClientFilter(realClient, new NoFilter<>())));
        } else {
            for (String client : validatedClients) {
                String className = AccessMap.get().getDetails(client).getFilter_classname();
                MessageFilter<KafkaEvent<?, ?>> currentFilter = getFilter(client, className);
                // 1:1 Mapping client:filter
                clientFilters.add(new ClientFilter(client, currentFilter));
            }
        }
        // The looper keeps the main thread alive while the GRPC Server does
        // it's business
        // Prints a heart beat to the logs every 60 seconds.
        LOGGER.debug("Server looper starting...");
        FederatorServer.Looper looper = new Looper();
        List<Future<?>> futureList = new ArrayList<>();
        futureList.add(THREADED_EXECUTOR.submit(looper));
        // Split out headers to process.
        String sHeaders = PropertyUtil.getPropertyValue(SHARED_HEADERS, CONTENT_TYPE);
        Set<String> sharedHeaders = Set.of(sHeaders.split(SHARED_HEADER_SEPARATOR));
        LOGGER.info("Shared Headers - '{}'", sharedHeaders);
        LOGGER.info("Start GRPC Server process");
        try (GRPCServer server = new GRPCServer(clientFilters, sharedHeaders)) {
            futureList.add(THREADED_EXECUTOR.submit(server::start));
            ThreadUtil.awaitShutdown(futureList, server, THREADED_EXECUTOR);
            LOGGER.debug("Post ThreadUtil Shutdown");
        } catch (Exception e) {
            LOGGER.error("MessageServeable failed to close.", e);
        }
        LOGGER.info("Server stopped.");
    }

    /**
     * Gets a MessageFilter implementation for the given client based on a class name found on the classpath.
     * <p>
     * If the className is empty, a default filter implementation is used.
     * </p>
     *
     * @param client the client identifier
     * @param className the fully-qualified class name of the filter to instantiate; if blank, a default is used
     * @return a MessageFilter instance for the client
     */
    @SuppressWarnings("java:S1452") // SonarQube rule: Remove usage of generic wildcard type.
    public static MessageFilter<KafkaEvent<?, ?>> getFilter(String client, String className) {
        if (className.isEmpty()) {
            LOGGER.info(
                    "Filter class name for {} is empty so creating default MessageFilter {}",
                    client,
                    DEFAULT_FILTER_CLASSNAME);
            className = DEFAULT_FILTER_CLASSNAME;
        }
        LOGGER.info("Attempting to creating MessageFilter from Classpath - '{}'", className);
        return FilterReflectiveCreator.getMessageFilter(client, className);
    }

    /**
     * Simple class to keep threads running
     * <p>
     * Provides a blocking loop that exits when a close flag is set. Close can be called from outside
     * the thread.
     */
    static class Looper implements Runnable, Closeable {

        private boolean close = false;

        /**
         * Runs a simple loop to keep the server process alive until {@link #close()} is invoked.
         * Logs a heartbeat at a regular cadence.
         */
        @Override
        public void run() {
            LOGGER.debug("Starting while loop");
            while (!close) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    LOGGER.info("Stopping Looper.");
                }
            }
            LOGGER.info("Looper stopped");
        }

        /**
         * Requests the loop to stop and returns immediately.
         *
         * @throws IOException unused but required by Closeable signature
         */
        @Override
        public void close() throws IOException {
            this.close = true;
            LOGGER.info("Stopping Looper.");
        }
    }
}
