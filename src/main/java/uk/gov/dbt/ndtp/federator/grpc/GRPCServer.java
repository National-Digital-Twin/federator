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
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.utils.ClientFilter;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.ThreadUtil;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * GRPCServer is a server for the FederatorService GRPC service.
 * It is used to start the GRPC server.
 * It is also used to generate the GRPC server with filters and shared headers.
 * It is also used to close the GRPC server.
 */
public class GRPCServer implements AutoCloseable {
	
	public static final String SERVER_PORT = "server.port";
	public static final String DEFAULT_PORT = "8080";
	public static final String SERVER_KEEP_ALIVE_TIME = "server.keepAliveTime";
	public static final String SERVER_KEEP_ALIVE_TIMEOUT = "server.keepAliveTimeout";
	public static final String SERVER_TLS_ENABLED = "server.tlsEnabled";
	public static final String SERVER_CERT_CHAIN_FILE_PATH = "server.certChainFile";
	public static final String SERVER_PRIVATE_KEY_FILE_PATH = "server.privateKeyFile";
	public static final String SERVER_CA_PEM_FILE_PATH = "server.caPem";
	public static final String FIVE = "5";
	public static final String ONE = "1";
	public static final String TRUE = "true";
	
	public static final Logger LOGGER = LoggerFactory.getLogger("GRPCServer");
	
	private final Server server;
	
	private ServerCredentials creds;
	
	public GRPCServer(List<ClientFilter> filters, Set<String> sharedHeaders) {
		if (PropertyUtil.getPropertyBooleanValue(SERVER_TLS_ENABLED, TRUE)) {
			creds = generateServerCredentials();
			server = generateServer(creds, filters, sharedHeaders);
		} else {
			LOGGER.warn("Server TLS is not enabled, using insecure server.");
			server = generateServer(filters, sharedHeaders);
		}
	}
	
	public static Server generateServer(ServerCredentials creds, List<ClientFilter> filters, Set<String> sharedHeaders) {
		
		ServerBuilder<?> builder = Grpc.newServerBuilderForPort(PropertyUtil.getPropertyIntValue(SERVER_PORT, DEFAULT_PORT), creds)
			.executor(ThreadUtil.threadExecutor("GRPCServer"))
			.keepAliveTime(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIME, FIVE), TimeUnit.SECONDS)
			.keepAliveTimeout(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIMEOUT, ONE), TimeUnit.SECONDS);
		
		builder.addService(ServerInterceptors.intercept(
			new GRPCFederatorService(filters, sharedHeaders), new CustomServerInterceptor()));
		
		return builder.build();
	}
	
	public static Server generateServer(List<ClientFilter> filters, Set<String> sharedHeaders) {
		ServerBuilder<?> builder = ServerBuilder.forPort(PropertyUtil.getPropertyIntValue(SERVER_PORT, DEFAULT_PORT))
			.executor(ThreadUtil.threadExecutor("GRPCServer"))
			.keepAliveTime(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIME, FIVE), TimeUnit.SECONDS)
			.keepAliveTimeout(PropertyUtil.getPropertyIntValue(SERVER_KEEP_ALIVE_TIMEOUT, ONE), TimeUnit.SECONDS);
		builder.addService(ServerInterceptors.intercept(
			new GRPCFederatorService(filters, sharedHeaders), new CustomServerInterceptor()));
		
		return builder.build();
	}
	
	@SneakyThrows
	private ServerCredentials generateServerCredentials() {
		
		String certChainFilePath = PropertyUtil.getPropertyValue(SERVER_CERT_CHAIN_FILE_PATH);
		String serverPrivateKeyFilePath = PropertyUtil.getPropertyValue(SERVER_PRIVATE_KEY_FILE_PATH);
		String rootCertPath = PropertyUtil.getPropertyValue(SERVER_CA_PEM_FILE_PATH);
		LOGGER.info("Generating server credentials with certChainFilePath: {}, serverPrivateKeyFilePath: {}, rootCertPath: {}",
			certChainFilePath, serverPrivateKeyFilePath, rootCertPath);
		
		if (certChainFilePath == null || serverPrivateKeyFilePath == null || rootCertPath == null) {
			LOGGER.error("Server certificate chain, private key, or CA PEM file path is not set.");
			throw new IllegalArgumentException("Server certificate chain, private key, or CA PEM file path is not set.");
		}
		TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
			.keyManager(getClass().getResourceAsStream(certChainFilePath), getClass().getResourceAsStream(serverPrivateKeyFilePath))
			.trustManager(getClass().getResourceAsStream(rootCertPath))
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
