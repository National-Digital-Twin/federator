// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the
// National Digital Twin
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.client.grpc.GRPCClient;
import uk.gov.dbt.ndtp.federator.client.jobs.DefaultJobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.client.jobs.JobSchedulerProvider;
import uk.gov.dbt.ndtp.federator.client.jobs.handlers.ClientDynamicConfigJob;
import uk.gov.dbt.ndtp.federator.common.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.common.service.config.ConsumerConfigService;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.storage.InMemoryConfigurationStore;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.common.utils.ObjectMapperUtil;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.ConfigurationException;

/**
 * Main class for the Federator client.
 *
 * <p>The Federator client is responsible for connecting to the Federator server and consuming
 * messages from the Kafka topic. The client is configured with a set of connection properties that
 * define the server to connect to and the Kafka topic to consume from.
 */
public class FederatorClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederatorClient.class);

    private static final long KEEP_ALIVE_INTERVAL = 10000L; // 10 seconds

    // Add a flag to control the keep-alive behavior
    private static final String TEST_MODE_PROPERTY = "federator.test.mode";

    private static final String ENV_CLIENT_PROPS = "FEDERATOR_CLIENT_PROPERTIES";
    private static final String DEFAULT_PROPS = "client.properties";
    private static final String KAFKA_PREFIX_KEY = "kafka.topic.prefix";
    private static final String EMPTY = "";
    private static final int HTTP_TIMEOUT = 10;
    private static final int EXIT_ERROR = 1;
    private static final String LOG_INIT = "Initializing Federator Client";
    private static final String LOG_STOPPED = "Client stopped";
    private static final String LOG_SERVICE = "Configuration service initialized";
    private static final String LOG_ERROR = "Configuration error, stopping: {}";
    private static final String LOG_PROPS_LOAD = "Loading properties from: {}";
    private static final String COMMON_CONFIG = "common.configuration";
    private static final String TRUSTSTORE_PATH = "idp.truststore.path";
    private static final String TRUSTSTORE_PASS = "idp.truststore.password";
    private static final String KEYSTORE_PATH = "idp.keystore.path";
    private static final String KEYSTORE_PASS = "idp.keystore.password";
    private static final String JKS_TYPE = "JKS";
    private static final String PKCS12_TYPE = "PKCS12";
    private static final String TLS_PROTOCOL = "TLS";
    private static final String LOG_SSL_CONFIG = "SSL configured with truststore: {}, keystore: {}";
    private static final String ERR_FILE_NOT_FOUND = "{} not found: {}";
    private static final String ERR_SSL_CONFIG = "SSL configuration failed: {}";
    private final ConsumerConfigService configService;
    private final JobSchedulerProvider scheduler;
    private final ExitHandler exitHandler;

    /**
     * Creates client with specified dependencies.
     *
     * @param builder GRPC client builder
     * @param service configuration service
     * @param scheduler job scheduler provider
     */
    public FederatorClient(
            final GRPCClientBuilder builder,
            final ConsumerConfigService service,
            final JobSchedulerProvider scheduler) {
        this(builder, service, scheduler, new SystemExitHandler());
    }

    /**
     * Creates client with all dependencies.
     *
     * @param builder GRPC client builder
     * @param service configuration service
     * @param scheduler job scheduler provider
     * @param exitHandler exit handler
     */
    FederatorClient(
            final GRPCClientBuilder builder,
            final ConsumerConfigService service,
            final JobSchedulerProvider scheduler,
            final ExitHandler exitHandler) {
        this.configService = service;
        this.scheduler = scheduler;
        this.exitHandler = exitHandler;
    }

    /**
     * Main entry point for the application.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        LOGGER.info(LOG_INIT);
        initProperties();
        final String prefix = PropertyUtil.getPropertyValue(KAFKA_PREFIX_KEY, EMPTY);
        final ConsumerConfigService service = createConfigService();
        ClientDynamicConfigJob.initialize(service);
        final JobSchedulerProvider scheduler = new DefaultJobSchedulerProvider();
        ClientDynamicConfigJob.setScheduler(scheduler);
        new FederatorClient(config -> new GRPCClient(config, prefix), service, scheduler).run();
    }

    /**
     * Validates and initialises the Federator connection properties from the configured location.
     *
     * <p>Ensures the configuration file is present, readable and contains at least one connection.
     * Throws a ConfigurationException with a useful message if validation fails.
     */
    private static void initProperties() {
        final String envProps = System.getenv(ENV_CLIENT_PROPS);
        if (envProps != null) {
            final File file = new File(envProps);
            if (file.exists()) {
                LOGGER.info(LOG_PROPS_LOAD, envProps);
                PropertyUtil.init(file);
                return;
            }
        }
        try {
            LOGGER.info(LOG_PROPS_LOAD, DEFAULT_PROPS);
            PropertyUtil.init(DEFAULT_PROPS);
        } catch (Exception ex) {
            LOGGER.error("Failed to load properties from: {}", DEFAULT_PROPS, ex);
            throw new ConfigurationException("Properties not found, using defaults:" + DEFAULT_PROPS);
        }
    }

    /**
     * Creates configuration service.
     *
     * @return configured service
     */
    private static ConsumerConfigService createConfigService() {
        final HttpClient httpClient = createHttpClient();
        final ObjectMapper mapper = ObjectMapperUtil.getInstance();

        final IdpTokenService tokenService = GRPCUtils.createIdpTokenService();
        final ManagementNodeDataHandler handler = new ManagementNodeDataHandler(httpClient, mapper, tokenService);
        final InMemoryConfigurationStore store = InMemoryConfigurationStore.getInstance();
        LOGGER.info(LOG_SERVICE);
        return new ConsumerConfigService(handler, store);
    }

    /**
     * Creates HTTP client with SSL if configured.
     *
     * @return HTTP client
     */
    private static HttpClient createHttpClient() {
        try {
            final Properties props = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG);
            return createSecureClient(props);
        } catch (Exception e) {
            LOGGER.error(ERR_SSL_CONFIG, e.getMessage());
            return createDefaultClient();
        }
    }

    private static HttpClient createSecureClient(final Properties props) throws IOException {
        final String trustPath = props.getProperty(TRUSTSTORE_PATH);
        final String trustPass = props.getProperty(TRUSTSTORE_PASS);
        if (trustPath == null || trustPass == null) {
            LOGGER.info("Creating default(unsecure) HTTP client");
            return createDefaultClient();
        }
        validateFile(trustPath, "Truststore");
        final String keyPath = props.getProperty(KEYSTORE_PATH);
        final String keyPass = props.getProperty(KEYSTORE_PASS);
        if (keyPath != null) {
            validateFile(keyPath, "Keystore");
        }
        final SSLContext ssl = buildSSLContext(trustPath, trustPass, keyPath, keyPass);
        LOGGER.info(LOG_SSL_CONFIG, trustPath, keyPath);
        return HttpClient.newBuilder()
                .sslContext(ssl)
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT))
                .build();
    }

    @SneakyThrows
    private static SSLContext buildSSLContext(
            final String trustPath, final String trustPass, final String keyPath, final String keyPass) {
        final KeyStore trustStore = loadKeyStore(trustPath, trustPass, JKS_TYPE);
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        final SSLContext ssl = SSLContext.getInstance(TLS_PROTOCOL);
        if (keyPath != null && keyPass != null) {
            final KeyStore keyStore = loadKeyStore(keyPath, keyPass, PKCS12_TYPE);
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyPass.toCharArray());
            ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            ssl.init(null, tmf.getTrustManagers(), null);
        }
        return ssl;
    }

    private static KeyStore loadKeyStore(final String path, final String password, final String type)
            throws KeyStoreException {
        final KeyStore keyStore = KeyStore.getInstance(type);
        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password.toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new KeyStoreException("Failed to load key store", e);
        }
        return keyStore;
    }

    private static void validateFile(final String path, final String type) throws IOException {
        final File file = new File(path);
        if (!file.exists()) {
            throw new IOException(String.format(ERR_FILE_NOT_FOUND, type, path));
        }
    }

    private static HttpClient createDefaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT))
                .build();
    }

    /** Runs the client lifecycle. */
    public void run() {
        try {
            scheduler.ensureStarted();
            registerDynamicJob();

            // Only run keep-alive loop if not in test mode
            if (!Boolean.getBoolean(TEST_MODE_PROPERTY)) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    LOGGER.info("Shutdown signal received, stopping client");
                    scheduler.stop();
                }));

                LOGGER.info("Client started, press Ctrl+C to stop");
                while (!Thread.currentThread().isInterrupted()) {
                    if (sleepKeepAliveInterval()) {
                        LOGGER.info("Service gracefully stopped");

                        break;
                    }
                }
            }

            LOGGER.info(LOG_STOPPED);
        } catch (ConfigurationException e) {
            handleError(e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error: {}", e.getMessage(), e);
            exitHandler.exit(EXIT_ERROR);
        }
    }

    /**
     * Sleeps for the keep-alive interval.
     * @return true if the thread was interrupted during sleep, false otherwise
     */
    private boolean sleepKeepAliveInterval() {
        try {
            Thread.sleep(KEEP_ALIVE_INTERVAL);
            return false;
        } catch (InterruptedException ex) {
            LOGGER.info("Client interrupted, {}", ex.getMessage());
            Thread.currentThread().interrupt();
            return true;
        }
    }

    void handleError(final ConfigurationException e) {
        LOGGER.error(LOG_ERROR, e.getMessage());
        exitHandler.exit(EXIT_ERROR);
    }

    private void registerDynamicJob() {
        new ClientDynamicConfigJob(configService, scheduler).register();
    }

    /** Interface for building GRPC clients. */
    public interface GRPCClientBuilder {
        /**
         * Builds a GRPC client.
         *
         * @param config connection properties
         * @return configured GRPC client
         */
        GRPCClient build(ConnectionProperties config);
    }

    /** Interface for handling system exit. */
    interface ExitHandler {
        /**
         * Exits the application.
         *
         * @param code exit code
         */
        void exit(int code);
    }

    /** Default exit handler implementation. */
    static class SystemExitHandler implements ExitHandler {
        @Override
        public void exit(final int code) {
            System.exit(code);
        }
    }
}
