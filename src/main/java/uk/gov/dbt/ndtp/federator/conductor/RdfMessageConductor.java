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

import java.util.Set;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.consumer.ClientTopicOffsets;
import uk.gov.dbt.ndtp.federator.consumer.KafkaEventMessageConsumer;
import uk.gov.dbt.ndtp.federator.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.processor.MessageProcessor;
import uk.gov.dbt.ndtp.federator.processor.RdfKafkaEventMessageProcessor;
import uk.gov.dbt.ndtp.secure.agent.payloads.RdfPayload;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.serializers.RdfPayloadDeserializer;

/**
 * Message processor for Kafka Events (of RDF Payloads)
 */
public class RdfMessageConductor extends AbstractKafkaEventMessageConductor<String, RdfPayload> {

    public static final Logger LOGGER = LoggerFactory.getLogger("RdfMessageProcessor");

    private final StreamObservable serverCallStreamObserver;

    public RdfMessageConductor(
            ClientTopicOffsets topicData,
            StreamObservable serverCallStreamObserver,
            MessageFilter<KafkaEvent<?, ?>> filter,
            Set<String> sharedHeaders) {
        this(
                serverCallStreamObserver,
                new KafkaEventMessageConsumer<>(
                        StringDeserializer.class,
                        RdfPayloadDeserializer.class,
                        topicData.getTopic(),
                        topicData.getOffset(),
                        topicData.getClient()),
                filter,
                new RdfKafkaEventMessageProcessor(serverCallStreamObserver, sharedHeaders));
    }

    private RdfMessageConductor(
            StreamObservable serverCallStreamObserver,
            MessageConsumer<KafkaEvent<String, RdfPayload>> consumer,
            MessageFilter<KafkaEvent<?, ?>> filter,
            MessageProcessor<KafkaEvent<String, RdfPayload>> postProcessor) {

        super(consumer, filter, postProcessor);
        this.serverCallStreamObserver = serverCallStreamObserver;
    }

    @Override
    public boolean continueProcessing() {
        if (serverCallStreamObserver.isCancelled()) {
            LOGGER.info("Observer is closed on client end. Stop further processing.");
            messageConsumer.close();
            return false;
        }
        return messageConsumer.stillAvailable();
    }
}
