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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectionConfigurationTest {

    private final ObjectMapper mapper = new JsonMapper();

    @Test
    void deserialization_full() throws JsonProcessingException {
        String json =
                """
                {
                  "credentials": {
                    "name": "client name",
                    "key": "client key"
                  },
                  "server": {
                    "host": "server host",
                    "port": 1234
                  },
                  "tls": {
                    "enabled": true
                  }
                }
                """;

        var actual = mapper.readValue(json, ConnectionConfiguration.class);

        assertEquals("client name", actual.getClientName());
        assertEquals("client key", actual.getClientKey());
        assertEquals("server host", actual.getServerHost());
        assertEquals(1234, actual.getServerPort());
        assertEquals(true, actual.getTls());
    }

    @Test
    void deserialization_partial() throws JsonProcessingException {
        String json =
                """
                {
                  "credentials": {
                    "name": "client name",
                    "key": "client key"
                  },
                  "server": {
                    "host": "server host"
                  }
                }
                """;

        var actual = mapper.readValue(json, ConnectionConfiguration.class);

        assertEquals("client name", actual.getClientName());
        assertEquals("client key", actual.getClientKey());
        assertEquals("server host", actual.getServerHost());
        assertNull(actual.getServerPort());
        assertNull(actual.getTls());
    }

    @Test
    void deserialization_list() throws JsonProcessingException {
        String json =
                """
                [
                  {
                    "credentials": {
                      "name": "client name",
                      "key": "client key"
                    },
                    "server": {
                      "host": "server host",
                      "port": 1234
                    },
                    "tls": {
                      "enabled": true
                    }
                  },
                  {
                    "credentials": {
                      "name": "client name 2",
                      "key": "client key 2"
                    },
                    "server": {
                      "host": "server host 2"
                    }
                  }
                ]
                """;

        var actual = mapper.readValue(json, new TypeReference<List<ConnectionConfiguration>>() {});

        assertEquals(2, actual.size());

        var properties = actual.get(0);
        assertEquals("client name", properties.getClientName());
        assertEquals("client key", properties.getClientKey());
        assertEquals("server host", properties.getServerHost());
        assertEquals(1234, properties.getServerPort());
        assertEquals(true, properties.getTls());

        properties = actual.get(1);
        assertEquals("client name 2", properties.getClientName());
        assertEquals("client key 2", properties.getClientKey());
        assertEquals("server host 2", properties.getServerHost());
        assertNull(properties.getServerPort());
        assertNull(properties.getTls());
    }
}
