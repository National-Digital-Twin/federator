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

package uk.gov.dbt.ndtp.federator.conductor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.exceptions.MessageProcessingException;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.federator.processor.MessageProcessor;

/**
 * Abstract representation of a message processor that leverages:
 * a consumer (to obtain messages)
 * a filter (to remove unnecessary messages)
 * a post-processor (to do something with the messages)
 * @param <MessageType> the type of message being processed.
 */
public abstract class AbstractMessageConductor<FilterMessageType, MessageType> implements MessageConductor {

    public static final Logger LOGGER = LoggerFactory.getLogger("AbstractMessageProcessor");

    protected final MessageConsumer<MessageType> messageConsumer;
    protected final MessageProcessor<MessageType> messageProcessor;
    protected final MessageFilter<FilterMessageType> messageFilter;

    protected AbstractMessageConductor(
            MessageConsumer<MessageType> consumer,
            MessageFilter<FilterMessageType> filter,
            MessageProcessor<MessageType> postProcessor) {
        messageConsumer = consumer;
        messageFilter = filter;
        messageProcessor = postProcessor;
    }

    @Override
    public void processMessages() throws MessageProcessingException {
        try {
            while (continueProcessing()) {
                processMessage();
            }
        } catch (Exception e) {
            throw new MessageProcessingException(e);
        }
    }

    @Override
    public boolean continueProcessing() {
        return messageConsumer.stillAvailable();
    }

    @Override
    public void close() {
        try {
            messageConsumer.close();
        } catch (Exception ex) {
            LOGGER.info("Error whilst closing consumer, ignoring.", ex);
        }
        try {
            messageFilter.close();
        } catch (Exception ex) {
            LOGGER.info("Error whilst closing filter, ignoring.", ex);
        }
        try {
            messageProcessor.close();
        } catch (Exception ex) {
            LOGGER.info("Error whilst closing post-processor, ignoring.", ex);
        }
    }
}
