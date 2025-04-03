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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectionsProperties {

    private static final Logger log = LoggerFactory.getLogger("ConnectionProperties");
    private static ConnectionsProperties instance;
    private final List<ConnectionProperties> config;

    private ConnectionsProperties(List<ConnectionProperties> config) {
        this.config = List.copyOf(config);
    }

    public static ConnectionsProperties get() {
        if (instance == null) {
            throw new ConfigurationException("Connection properties is not yet initialised, call #init(File) first");
        }
        return instance;
    }

    public List<ConnectionProperties> config() {
        return config;
    }

    public static ConnectionsProperties init(File file) {
        if (instance != null) {
            return instance;
        }
        // creating a mapper here as we should only be calling init once so there is no need to hold onto the reference
        var mapper = new JsonMapper();
        List<ConnectionConfiguration> configs;
        try {
            configs = mapper.readValue(file, new TypeReference<>() {});
        } catch (IOException e) {
            throw new ConfigurationException.ConfigurationParsingException("Error when parsing " + file.getName(), e);
        }
        var config = configs.stream().map(ConnectionProperties::new).toList();

        log.info("Loaded connection configuration from {}", file.getPath());

        instance = new ConnectionsProperties(config);

        return instance;
    }

    /**
     * For test support
     */
    static void reset() {
        instance = null;
    }
}
