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
package uk.gov.dbt.ndtp.federator.server.conductor;

import static uk.gov.dbt.ndtp.federator.common.utils.HeaderUtils.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.exceptions.MessageProcessingException;
import uk.gov.dbt.ndtp.federator.server.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Abstract representation of a message processor for Secure Agent Kafka Events
 * (with Key/Event)
 *
 * @param <Key>   the datatype of the event's key
 * @param <Value> the underlying datatype for the event
 */
public abstract class AbstractKafkaEventMessageConductor<Key, Value>
        extends AbstractMessageConductor<KafkaEvent<?, ?>, KafkaEvent<Key, Value>> {

    public static final Logger LOGGER = LoggerFactory.getLogger("AbstractKafkaEventMessageProcessor");

    public AbstractKafkaEventMessageConductor(
            MessageConsumer<KafkaEvent<Key, Value>> consumer,
            MessageProcessor<KafkaEvent<Key, Value>> postProcessor,
            List<AttributesDTO> filterAttributes) {
        super(consumer, postProcessor, filterAttributes);
    }

    @Override
    public void processMessages() throws MessageProcessingException {
        try {
            while (continueProcessing()) {
                processMessage();
            }
        } catch (Exception e) {
            LOGGER.error("Exception encountered:", e);
            throw new MessageProcessingException(e);
        }
    }

    @Override
    public void processMessage() {
        LOGGER.debug("Before messageConsumer.getNextMessage() .... ");
        KafkaEvent<Key, Value> kafkaEvent = messageConsumer.getNextMessage();
        LOGGER.debug("After messageConsumer.getNextMessage() .... ");
        if (kafkaEvent == null) {
            LOGGER.debug("Timed out waiting for Consumer to return more events, continue waiting");
        } else {
            long offset = kafkaEvent.getConsumerRecord().offset();
            Key key = kafkaEvent.key();

            if (isEventAllowed(kafkaEvent)) {
                LOGGER.debug("Before messageProcessor.process(kafkaEvent) .... ");
                messageProcessor.process(kafkaEvent);
                String headers = kafkaEvent.headers().map(Header::toString).collect(Collectors.joining(","));
                LOGGER.info("Processed message. Offset: '{}'. Key: '{}'. Kafka Header: '{}'", offset, key, headers);
            } else {
                LOGGER.debug("Filtering out message due to attribute filter. Offset: '{}'. Key: '{}'", offset, key);
            }
        }
    }

    /**
     * Determines whether the given Kafka event should be allowed through based on header attributes.
     * Rules:
     * 1) If no filterAttributes are configured, allow all messages.
     * 2) If one attribute is set, require the corresponding header key to exist and value to match.
     * 3) If multiple attributes are set, require ALL of them to match (AND semantics).
     */
    protected boolean isEventAllowed(KafkaEvent<Key, Value> kafkaEvent) {
        if (filterAttributes == null || filterAttributes.isEmpty()) {
            return true; // Rule 1
        }

        String secLabel = getSecurityLabelFromHeaders(kafkaEvent.headers());
        LOGGER.debug("Processing Message. SecLabel for message {}", secLabel);

        Map<String, String> headerMap = getMapFromSecurityLabel(secLabel);
        LOGGER.debug("Headers map: {} , Filtering attributes: {}", headerMap, filterAttributes);

        // AND semantics across all configured attributes
        for (AttributesDTO attr : filterAttributes) {
            if (attr == null) continue; // ignore null entries defensively
            String name = attr.getName();
            String expectedValue = attr.getValue();
            if (name == null || expectedValue == null) {
                return false; // invalid filter definition => do not allow
            }
            String actual = headerMap.get(name.toUpperCase(Locale.ROOT));
            if (actual == null) {
                return false; // header missing for required attribute
            }
            if (!actual.equalsIgnoreCase(expectedValue)) {
                return false; // value mismatch
            }
        }
        return true;
    }
}
