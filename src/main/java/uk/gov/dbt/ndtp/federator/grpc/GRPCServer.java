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
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.utils.ClientFilter;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.SSLUtils;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;

/**
 * GRPCServer is a server for the FederatorService GRPC service.
 * It is used to start the GRPC server.
 * It is also used to generate the GRPC server with filters and shared headers.
 * It is also used to close the GRPC server.
 */
public class GRPCServer implements AutoCloseable {
    public static final String GRPC_SERVER = "GRPCServer";
    public static final Logger LOGGER = LoggerFactory.getLogger(GRPC_SERVER);

    public static final String SERVER_PORT = "server.port";
    public static final String DEFAULT_PORT = "8080";
    public static final String SERVER_KEEP_ALIVE_TIME = "server.keepAliveTime";
    public static final String SERVER_KEEP_ALIVE_TIMEOUT = "server.keepAliveTimeout";
    public static final String SERVER_MTLS_ENABLED = "server.mtlsEnabled";
    public static final String FIVE = "5";
    public static final String ONE = "1";
    public static final String FALSE = "false";

    private static final String SERVER_P12_FILE_PATH = "server.p12FilePath";
    private static final String SERVER_P12_PASSWORD = "server.p12Password";
    private static final String SERVER_TRUSTSTORE_FILE_PATH = "server.truststoreFilePath";
    private static final String SERVER_TRUSTSTORE_PASSWORD = "server.truststorePassword";
    private final Server server;

    private ServerCredentials creds;

    public GRPCServer(List<ClientFilter> filters, Set<String> sharedHeaders) {
        if (PropertyUtil.getPropertyBooleanValue(SERVER_MTLS_ENABLED, FALSE)) {
            creds = generateServerCredentials();
            server = generateSecureServer(creds, filters, sharedHeaders);
        } else {
            LOGGER.warn("Server TLS is not enabled, using insecure server.");
            server = generateServer(filters, sharedHeaders);
        }
    }

    private Server generateSecureServer(
            ServerCredentials creds, List<ClientFilter> filters, Set<String> sharedHeaders) {

        ServerBuilder<?> builder = Grpc.newServerBuilderForPort(
                        PropertyUtil.getPropertyIntValue(SERVER_PORT, DEFAULT_PORT), creds)
                .executor(ThreadUtil.threadExecutor(GRPC_SERVER))
                .keepAliveTime(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIME, FIVE), TimeUnit.SECONDS)
                .keepAliveTimeout(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIMEOUT, ONE), TimeUnit.SECONDS);

        builder.addService(ServerInterceptors.intercept(
                new GRPCFederatorService(filters, sharedHeaders),
                new uk.gov.dbt.ndtp.federator.grpc.interceptor.CustomServerInterceptor()));

        return builder.build();
    }

    private Server generateServer(List<ClientFilter> filters, Set<String> sharedHeaders) {
        ServerBuilder<?> builder = ServerBuilder.forPort(PropertyUtil.getPropertyIntValue(SERVER_PORT, DEFAULT_PORT))
                .executor(ThreadUtil.threadExecutor(GRPC_SERVER))
                .keepAliveTime(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIME, FIVE), TimeUnit.SECONDS)
                .keepAliveTimeout(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIMEOUT, ONE), TimeUnit.SECONDS);
        builder.addService(ServerInterceptors.intercept(
                new GRPCFederatorService(filters, sharedHeaders),
                new uk.gov.dbt.ndtp.federator.grpc.interceptor.CustomServerInterceptor()));

        return builder.build();
    }

    @SneakyThrows
    private ServerCredentials generateServerCredentials() {

        String p12FilePath = PropertyUtil.getPropertyValue(SERVER_P12_FILE_PATH);
        String p12Password = PropertyUtil.getPropertyValue(SERVER_P12_PASSWORD);
        String trustStoreFilePath = PropertyUtil.getPropertyValue(SERVER_TRUSTSTORE_FILE_PATH);
        String trustStorePassword = PropertyUtil.getPropertyValue(SERVER_TRUSTSTORE_PASSWORD);

        LOGGER.info(
                "Using p12 file path: {}, truststore file path: {}, p12 password is set: {}, truststore password is set: {}",
                p12FilePath,
                trustStoreFilePath,
                p12Password != null,
                trustStorePassword != null);

        KeyManager[] keyManagerFromP12 = SSLUtils.createKeyManagerFromP12(p12FilePath, p12Password);
        TrustManager[] trustManager = SSLUtils.createTrustManager(trustStoreFilePath, trustStorePassword);

        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                .keyManager(keyManagerFromP12)
                .trustManager(trustManager)
                .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);

        return tlsBuilder.build();
    }

    public void start() {
        try {
            LOGGER.info("GRPCServer starting");
            server.start();
        } catch (IOException e) {
            LOGGER.error("Exception encountered starting GRPC Server", e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            LOGGER.info("GRPCServer close called");
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            LOGGER.info("GRPCServer closed");
        } catch (InterruptedException e) {
            LOGGER.error("Exception occurred during shutdown", e);
        }
    }
}
