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
package uk.gov.dbt.ndtp.federator.conductor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.federator.processor.MessageProcessor;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;

/**
 * Abstract representation of a message processor for Secure Agent Events (with Key/Event)
 *
 * @param <Key>   the datatype of the event's key
 * @param <Value> the underlying datatype for the event
 */
public abstract class AbstractEventMessageConductor<Key, Value>
        extends AbstractMessageConductor<Event<Key, Value>, Event<Key, Value>> {

    public static final Logger LOGGER = LoggerFactory.getLogger("AbstractEventMessageProcessor");

    private final AtomicInteger counter = new AtomicInteger(0);

    AbstractEventMessageConductor(
            MessageConsumer<Event<Key, Value>> consumer,
            MessageFilter<Event<Key, Value>> filter,
            MessageProcessor<Event<Key, Value>> postProcessor) {
        super(consumer, filter, postProcessor);
    }

    @Override
    public void processMessage() {
        Event<Key, Value> event = messageConsumer.getNextMessage();
        if (event == null) {
            LOGGER.debug("Timed out waiting for Consumer to return more events, continue waiting");
        } else {
            Key key = event.key();
            try {
                boolean filterOut = messageFilter.filterOut(event);
                if (filterOut) {
                    LOGGER.info("Filtering out message: '{}'. Event key: '{}'", counter.getAndIncrement(), key);
                } else {
                    messageProcessor.process(event);
                    String headers = event.headers().map(Header::toString).collect(Collectors.joining(","));
                    LOGGER.info("Processed message, Event key: '{}'', headers: '{}'", key, headers);
                }
            } catch (LabelException e) {
                // TODO If the labels are incorrect the message will be skipped. Feels like we need a reload tool.
                String errMsg = String.format(
                        "Label format issue: '%s'. Filtering out message: '%s'. Event key: '%s'",
                        e.getMessage(), (counter.getAndIncrement()), key);
                LOGGER.error(errMsg);
            }
        }
    }
}
