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

package uk.gov.dbt.ndtp.federator.utils;

import java.util.Objects;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Represents a filter for a client
 * @param clientId the client ID to filter messages for.  Cannot be null or empty.
 * @param messageFilter the filter to apply to messages.  Cannot be null.
 */
public record ClientFilter(String clientId, MessageFilter<KafkaEvent<?, ?>> messageFilter) {
    public ClientFilter {
        StringUtils.throwIfBlank(clientId, () -> new IllegalArgumentException("Client ID cannot be null or empty"));
        Objects.requireNonNull(messageFilter);
    }
}
