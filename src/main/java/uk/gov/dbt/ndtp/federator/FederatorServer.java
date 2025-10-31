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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.ThreadUtil;
import uk.gov.dbt.ndtp.federator.server.grpc.GRPCServer;

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

    public static final String ENV_SERVER_PROPS = "FEDERATOR_SERVER_PROPERTIES";
    public static final String SERVER_PROPERTIES = "server.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger("FederatorServer");
    private static final String SHARED_HEADERS = "shared.headers";
    private static final String HEADER_SEPARATOR = "\\^";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final int LOOP_SLEEP = 100;
    private static final ExecutorService THREADED_EXECUTOR = ThreadUtil.threadExecutor("Server");

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
    public static void main(final String[] args) {
        // Initialize properties if not already done
        if (!PropertyUtil.initializeProperties()) {
            LOGGER.error("Failed to initialize properties. Exiting.");
            System.exit(1);
        }

        LOGGER.debug("Server looper starting...");
        List<Future<?>> futureList = new ArrayList<>();
        futureList.add(THREADED_EXECUTOR.submit(new Looper()));

        LOGGER.info("Start GRPC Server process");
        String sHeaders = PropertyUtil.getPropertyValue(SHARED_HEADERS, CONTENT_TYPE);
        Set<String> sharedHeaders = Set.of(sHeaders.split(HEADER_SEPARATOR));
        LOGGER.info("Shared Headers - '{}'", sharedHeaders);
        try (GRPCServer server = new GRPCServer(sharedHeaders)) {
            futureList.add(THREADED_EXECUTOR.submit(server::start));
            ThreadUtil.awaitShutdown(futureList, server, THREADED_EXECUTOR);
            LOGGER.debug("Post ThreadUtil Shutdown");
        } catch (Exception e) {
            LOGGER.error("MessageServeable failed to close.", e);
        }
        LOGGER.info("Server stopped.");
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
