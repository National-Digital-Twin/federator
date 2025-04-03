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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectionPropertiesTest {

    private static final String VALID_NAME = "client name";
    private static final String VALID_SERVER_NAME = "serverName";
    private static final String VALID_KEY = "client key";
    private static final String VALID_HOST = "server host";

    @Test
    void init_from_config() {
        ConnectionConfiguration config = new ConnectionConfiguration(
                new ConnectionConfiguration.CredentialsProperties(VALID_NAME, VALID_KEY),
                new ConnectionConfiguration.ServerProperties(VALID_SERVER_NAME, VALID_HOST, 123),
                new ConnectionConfiguration.TlsProperties(true));

        ConnectionProperties actual = new ConnectionProperties(config);

        ConnectionProperties expected =
                new ConnectionProperties(VALID_NAME, VALID_KEY, VALID_SERVER_NAME, VALID_HOST, 123, true);
        assertEquals(expected, actual);
    }

    @Test
    void init_from_config_with_defaults() {
        ConnectionConfiguration config = new ConnectionConfiguration(
                new ConnectionConfiguration.CredentialsProperties(VALID_NAME, VALID_KEY),
                new ConnectionConfiguration.ServerProperties(VALID_SERVER_NAME, VALID_HOST, null),
                null);

        ConnectionProperties actual = new ConnectionProperties(config);

        ConnectionProperties expected =
                new ConnectionProperties(VALID_NAME, VALID_KEY, VALID_SERVER_NAME, VALID_HOST, 8080, false);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void constraints_client_name(String name) {
        var exception = assertThrows(
                ConfigurationException.class,
                () -> new ConnectionProperties(name, VALID_KEY, VALID_SERVER_NAME, VALID_HOST, 123, true));
        assertEquals("The client name requires a value", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void constraints_client_key(String key) {
        var exception = assertThrows(
                ConfigurationException.class,
                () -> new ConnectionProperties(VALID_NAME, key, VALID_SERVER_NAME, VALID_HOST, 123, true));
        assertEquals("The client key requires a value", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void constraints_server_name_empty(String serverName) {
        var exception = assertThrows(
                ConfigurationException.class,
                () -> new ConnectionProperties(VALID_NAME, VALID_KEY, serverName, VALID_HOST, 123, true));
        assertEquals("The server name requires a value.  Permitted values are alphanumeric.", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"  bob  ", "b&b"})
    void constraints_server_name_invalid_chars(String serverName) {
        var exception = assertThrows(
                ConfigurationException.class,
                () -> new ConnectionProperties(VALID_NAME, VALID_KEY, serverName, VALID_HOST, 123, true));
        assertEquals("The server name requires a value.  Permitted values are alphanumeric.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void constraints_server_host(String host) {
        var exception = assertThrows(
                ConfigurationException.class,
                () -> new ConnectionProperties(VALID_NAME, VALID_KEY, VALID_SERVER_NAME, host, 123, true));
        assertEquals("The server host requires a value", exception.getMessage());
    }

    @Test
    void constraints_server_port() {
        var exception = assertThrows(
                ConfigurationException.class,
                () -> new ConnectionProperties(VALID_NAME, VALID_KEY, VALID_SERVER_NAME, VALID_HOST, -1, true));
        assertEquals("The server port(-1) requires a positive value", exception.getMessage());
    }
}
