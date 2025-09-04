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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
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
import uk.gov.dbt.ndtp.federator.jobs.DefaultJobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.service.FederatorConfigurationService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.service.IdpTokenServiceImpl;
import uk.gov.dbt.ndtp.federator.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.utils.ClientFilter;
import uk.gov.dbt.ndtp.federator.utils.FilterReflectiveCreator;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

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

    private static final Logger LOGGER =
            LoggerFactory.getLogger("FederatorServer");
    private static final String ENV_SERVER_PROPS =
            "FEDERATOR_SERVER_PROPERTIES";
    private static final String SERVER_PROPERTIES =
            "server.properties";
    private static final String FILTER_SHARE_ALL = "filter.shareAll";
    private static final String ACCESS_MAP_FILE =
            "server.accessMapValueFile";
    private static final String SHARED_HEADERS = "shared.headers";
    private static final String HEADER_SEPARATOR = "\\^";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FALSE = "false";
    private static final String DEFAULT_FILTER_CLASS =
            "uk.gov.dbt.ndtp.federator.filter." +
                    "KafkaEventHeaderAttributeAccessFilter";
    private static final int HTTP_TIMEOUT = 10;
    private static final int LOOP_SLEEP = 100;
    private static final ExecutorService THREADED_EXECUTOR =
            ThreadUtil.threadExecutor("Server");

    static {
        // Try to initialize from environment variable only
        String serverProperties = System.getenv(ENV_SERVER_PROPS);
        if (serverProperties != null) {
            File f = new File(serverProperties);
            if (f.exists() && f.isFile()) {
                PropertyUtil.init(f);
            }
        }
        // Don't try to load from resource here to avoid NPE
    }

    /**
     * Main entry point for the server.
     *
     * @param args command line arguments
     * @throws IOException if initialization fails
     */
    public static void main(final String[] args) throws IOException {
        // Initialize properties if not already done
        if (!initializeProperties()) {
            LOGGER.error("Failed to initialize properties. Exiting.");
            System.exit(1);
        }
        DefaultJobSchedulerProvider.getInstance().ensureStarted();
        initializeConfigurationService();
        String accessMapValueFile = PropertyUtil
                .getPropertyValue(ACCESS_MAP_FILE);
        if (accessMapValueFile != null &&
                !accessMapValueFile.isEmpty()) {
            LOGGER.info("Initialising access map from file: {}",
                    accessMapValueFile);
            AccessMap.initFromFile(new File(accessMapValueFile));
        } else {
            LOGGER.error("'{}' that points to client credentials missing",
                    ACCESS_MAP_FILE);
            System.exit(1);
        }
        boolean nofilter = PropertyUtil
                .getPropertyBooleanValue(FILTER_SHARE_ALL, FALSE);
        List<ClientFilter> clientFilters = new ArrayList<>();
        List<String> validatedClients = new ArrayList<>();
        for (String clientName : AccessMap.get().getClientNames()) {
            Optional<String> client = getValidClient(clientName);
            if (client.isPresent()) {
                validatedClients.add(client.get());
            } else {
                LOGGER.error("Cannot get client value for {}. Stopping.",
                        clientName);
                System.exit(1);
            }
        }
        if (nofilter) {
            LOGGER.info("Running using NoFilter ({}=true)",
                    FILTER_SHARE_ALL);
            validatedClients.forEach(c ->
                    clientFilters.add(new ClientFilter(
                            c, new NoFilter<>())));
        } else {
            for (String client : validatedClients) {
                String className = AccessMap.get()
                        .getDetails(client).getFilter_classname();
                clientFilters.add(new ClientFilter(
                        client, getFilter(client, className)));
            }
        }
        LOGGER.debug("Server looper starting...");
        List<Future<?>> futureList = new ArrayList<>();
        futureList.add(THREADED_EXECUTOR.submit(new Looper()));
        String sHeaders = PropertyUtil
                .getPropertyValue(SHARED_HEADERS, CONTENT_TYPE);
        Set<String> sharedHeaders = Set.of(
                sHeaders.split(HEADER_SEPARATOR));
        LOGGER.info("Shared Headers - '{}'", sharedHeaders);
        LOGGER.info("Start GRPC Server process");
        try (GRPCServer server = new GRPCServer(
                clientFilters, sharedHeaders)) {
            futureList.add(THREADED_EXECUTOR.submit(server::start));
            ThreadUtil.awaitShutdown(
                    futureList, server, THREADED_EXECUTOR);
            LOGGER.debug("Post ThreadUtil Shutdown");
        } catch (Exception e) {
            LOGGER.error("MessageServeable failed to close.", e);
        }
        LOGGER.info("Server stopped.");
    }

    private static void initializeConfigurationService() {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT))
                .build();
        final ObjectMapper mapper = new ObjectMapper();
        final IdpTokenService tokenService =
                new IdpTokenServiceImpl(httpClient, mapper);
        final ManagementNodeDataHandler handler =
                new ManagementNodeDataHandler(
                        httpClient, mapper, tokenService);
        new FederatorConfigurationService(handler,
                new InMemoryConfigurationStore());
        LOGGER.info("Configuration service initialized");
    }

    private static boolean initializeProperties() {
        try {
            // Check if already initialized by trying to get instance
            PropertyUtil.getInstance();
            return true;
        } catch (Exception e) {
            // Not initialized, so initialize now
            final String envProps = System.getenv(ENV_SERVER_PROPS);
            if (envProps != null) {
                final File file = new File(envProps);
                if (file.exists()) {
                    try {
                        PropertyUtil.init(file);
                        return true;
                    } catch (Exception ex) {
                        LOGGER.error("Failed to load properties from: {}",
                                file.getPath(), ex);
                        return false;
                    }
                }
                LOGGER.warn("File specified by {} not found: {}",
                        ENV_SERVER_PROPS, envProps);
            }

            // Try to load from classpath as last resort
            try {
                PropertyUtil.init(SERVER_PROPERTIES);
                return true;
            } catch (Exception ex) {
                LOGGER.error("Failed to load {} from classpath. " +
                        "Ensure file exists in resources directory or " +
                        "set {} to valid file path",
                        SERVER_PROPERTIES, ENV_SERVER_PROPS);
                return false;
            }
        }
    }

    private static Optional<String> getValidClient(
            final String client) {
        if (client == null || client.isEmpty()) {
            LOGGER.error("Shutting down as client not set in config");
            return Optional.empty();
        } else if (AccessMap.get().getDetails(client) == null) {
            LOGGER.error("Shutting down as client '{}' not found",
                    client);
            LOGGER.error("Likely '{}' is not key in '{}' json file",
                    client, ACCESS_MAP_FILE);
            return Optional.empty();
        }
        return Optional.of(client);
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
    public static MessageFilter<KafkaEvent<?, ?>> getFilter(
            final String client,
            String className) {
        final String filterClass = className.isEmpty()
                ? DEFAULT_FILTER_CLASS : className;
        if (className.isEmpty()) {
            LOGGER.info("Filter class empty for {}, using default: {}",
                    client, DEFAULT_FILTER_CLASS);
        }
        LOGGER.info("Creating MessageFilter from classpath: '{}'",
                filterClass);
        return FilterReflectiveCreator.getMessageFilter(
                client, filterClass);
    }

    /**
     * Simple loop to keep server alive.
     */
    static class Looper implements Runnable, Closeable {
        private volatile boolean close = false;


        /**
         * Runs a simple loop to keep the server process alive until {@link #close()} is invoked.
         * Logs a heartbeat at a regular cadence.
         */
        @Override
        public void run() {
            LOGGER.debug("Starting while loop");
            while (!close) {
                try {
                    Thread.sleep(LOOP_SLEEP);
                } catch (InterruptedException e) {
                    LOGGER.info("Stopping Looper.");
                    Thread.currentThread().interrupt();
                    break;
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
        public void close() {
            this.close = true;
            LOGGER.info("Stopping Looper.");
        }
    }
}