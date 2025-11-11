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

import static uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils.*;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.client.grpc.interceptor.AuthClientInterceptor;
import uk.gov.dbt.ndtp.federator.client.grpc.interceptor.CustomClientInterceptor;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.utils.GRPCUtils;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;

public interface GRPCAbstractClient extends AutoCloseable {
    Logger LOGGER = LoggerFactory.getLogger("GRPCAbstractClient");
    String CLIENT_KEEP_ALIVE_TIME = "client.keepAliveTime.secs";
    String CLIENT_KEEP_ALIVE_TIMEOUT = "client.keepAliveTimeout.secs";
    String CLIENT_IDLE_TIMEOUT = "client.idleTimeout.secs";
    String CLIENT_P12_FILE_PATH = "client.p12FilePath";
    String CLIENT_P12_PASSWORD = "client.p12Password";
    String CLIENT_TRUSTSTORE_FILE_PATH = "client.truststoreFilePath";
    String CLIENT_TRUSTSTORE_PASSWORD = "client.truststorePassword";
    String TEN = "10";
    String THIRTY = "30";

    default ManagedChannel generateChannel(String host, int port, boolean ismTLSEnabled) {
        if (ismTLSEnabled) {
            LOGGER.info("Using MTLS for GRPC connection");
            return generateSecureChannel(host, port, generateChannelCredentials());
        } else {
            LOGGER.info("Using plaintext for GRPC connection");
            return generateChannel(host, port);
        }
    }

    private ManagedChannel generateChannel(String host, int port) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        builder.usePlaintext();
        return configureChannelBuilder(builder).build();
    }

    private ManagedChannel generateSecureChannel(String host, int port, ChannelCredentials cred) {
        ManagedChannelBuilder<?> builder = Grpc.newChannelBuilderForAddress(host, port, cred);
        return configureChannelBuilder(builder).build();
    }

    private ManagedChannelBuilder<?> configureChannelBuilder(ManagedChannelBuilder<?> builder) {
        IdpTokenService tokenService = GRPCUtils.createIdpTokenService();
        return builder.keepAliveTime(PropertyUtil.getPropertyIntValue(CLIENT_KEEP_ALIVE_TIME, THIRTY), TimeUnit.SECONDS)
                .keepAliveTimeout(PropertyUtil.getPropertyIntValue(CLIENT_KEEP_ALIVE_TIMEOUT, TEN), TimeUnit.SECONDS)
                .idleTimeout(PropertyUtil.getPropertyIntValue(CLIENT_IDLE_TIMEOUT, TEN), TimeUnit.SECONDS)
                .intercept(new CustomClientInterceptor(), new AuthClientInterceptor(tokenService));
    }

    default String getRedisPrefix(String client, String serverName) {
        return client + "-" + serverName;
    }
}
