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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.exceptions.ConfigurationException;

class ConnectionsPropertiesTest {

    private static final File JSON;

    static {
        try {
            JSON = new File(Objects.requireNonNull(
                            Thread.currentThread().getContextClassLoader().getResource("connection-configuration.json"))
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        ConnectionsProperties.reset();
    }

    @Test
    void init() {
        ConnectionsProperties created = ConnectionsProperties.init(JSON);

        var config = created.config();
        var expected = List.of(
                new ConnectionProperties("client name", "client key", "ServerName1", "server host", 1234, true),
                new ConnectionProperties("client name 2", "client key 2", "ServerName2", "server host 2", 8080, false));
        assertEquals(expected, config);
    }

    @Test
    void init_handles_reading_issue() throws IOException {
        Path tmp = Files.createTempFile(null, null);

        try {
            Files.writeString(tmp, "not json");

            assertThrows(ConfigurationException.class, () -> ConnectionsProperties.init(tmp.toFile()));

        } finally {
            Files.delete(tmp);
        }
    }

    @Test
    void init_only_occurs_once() {
        var actual = ConnectionsProperties.init(JSON);
        var other = ConnectionsProperties.init(JSON);

        assertSame(actual, other);
    }

    @Test
    void get_throws_exception_if_not_initialised() {
        assertThrows(ConfigurationException.class, ConnectionsProperties::get);
    }

    @Test
    void get_does_not_throw_exception_if_initialised() {
        ConnectionsProperties.init(JSON);

        assertDoesNotThrow(ConnectionsProperties::get);
    }
}
