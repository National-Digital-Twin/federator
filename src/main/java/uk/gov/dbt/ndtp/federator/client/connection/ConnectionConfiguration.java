// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

/*
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

package uk.gov.dbt.ndtp.federator.client.connection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Configuration for the connection to a server.
 * This includes the credentials, server details and TLS configuration.
 * The configuration is read from a JSON file.
 * The configuration is validated and parsed before being used.
 */
final class ConnectionConfiguration {
    private final CredentialsProperties credentials;
    private final ServerProperties server;
    private final TlsProperties tls;

    @JsonCreator
    ConnectionConfiguration(
            @JsonProperty("credentials") CredentialsProperties credentials,
            @JsonProperty("server") ServerProperties server,
            @JsonProperty("tls") TlsProperties tls) {
        this.credentials = Objects.requireNonNull(credentials, "Credentials must not be null");
        this.server = Objects.requireNonNull(server, "Server must not be null");
        this.tls = tls;
    }

    public String getClientName() {
        return credentials.name();
    }

    public String getClientKey() {
        return credentials.key();
    }

    public String getServerName() {
        return server.name();
    }

    public String getServerHost() {
        return server.host();
    }

    public Integer getServerPort() {
        return server.port();
    }

    public Boolean getTls() {
        return tls == null ? null : tls.enabled();
    }

    record CredentialsProperties(String name, String key) {}

    record ServerProperties(String name, String host, Integer port) {}

    record TlsProperties(Boolean enabled) {}
}
