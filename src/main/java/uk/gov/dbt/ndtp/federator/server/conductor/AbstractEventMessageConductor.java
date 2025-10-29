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
package uk.gov.dbt.ndtp.federator.server.conductor;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.server.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;

/**
 * Abstract representation of a message processor for Secure Agent Events (with Key/Event)
 *
 * @param <K>   the datatype of the event's key
 * @param <V> the underlying datatype for the event
 */
public abstract class AbstractEventMessageConductor<K, V> extends AbstractMessageConductor<Event<K, V>, Event<K, V>> {

    public static final Logger LOGGER = LoggerFactory.getLogger("AbstractEventMessageProcessor");

    AbstractEventMessageConductor(
            MessageConsumer<Event<K, V>> consumer,
            List<AttributesDTO> filterAttributes,
            MessageProcessor<Event<K, V>> postProcessor) {
        super(consumer, postProcessor, filterAttributes);
    }

    @Override
    public void processMessage() {
        Event<K, V> event = messageConsumer.getNextMessage();
        if (event == null) {
            LOGGER.debug("Timed out waiting for Consumer to return more events, continue waiting");
        } else {
            K key = event.key();
            messageProcessor.process(event);
            String headers = event.headers().map(Header::toString).collect(Collectors.joining(","));
            LOGGER.info("Processed message, Event key: '{}'', headers: '{}'", key, headers);
        }
    }
}
