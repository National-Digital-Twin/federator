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
package uk.gov.dbt.ndtp.federator.client.connection;

import static uk.gov.dbt.ndtp.federator.utils.StringUtils.throwIfBlank;
import static uk.gov.dbt.ndtp.federator.utils.StringUtils.throwIfNotMatch;

import java.util.Objects;
import java.util.regex.Pattern;

public record ConnectionProperties(
        String clientName, String clientKey, String serverName, String serverHost, int serverPort, boolean tls) {

    public static final int DEFAULT_PORT = 8080;
    public static final boolean DEFAULT_TLS = false;
    private static final Pattern SERVER_NAME_REGEX = Pattern.compile("^[a-zA-Z0-9]+$");

    public ConnectionProperties {
        throwIfBlank(
                clientName,
                () -> new ConfigurationException.ConfigurationValidationException("The client name requires a value"));
        throwIfBlank(
                clientKey,
                () -> new ConfigurationException.ConfigurationValidationException("The client key requires a value"));

        throwIfBlank(
                serverHost,
                () -> new ConfigurationException.ConfigurationValidationException("The server host requires a value"));
        if (serverPort < 0) {
            throw new ConfigurationException.ConfigurationValidationException(
                    "The server port(" + serverPort + ") requires a positive value");
        }
        throwIfNotMatch(
                serverName,
                () -> new ConfigurationException.ConfigurationValidationException(
                        "The server name requires a value.  Permitted values are alphanumeric."),
                SERVER_NAME_REGEX);
    }

    ConnectionProperties(ConnectionConfiguration properties) {
        this(
                properties.getClientName(),
                properties.getClientKey(),
                properties.getServerName(),
                properties.getServerHost(),
                Objects.requireNonNullElse(properties.getServerPort(), DEFAULT_PORT),
                Objects.requireNonNullElse(properties.getTls(), DEFAULT_TLS));
    }
}
