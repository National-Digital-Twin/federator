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

package uk.gov.dbt.ndtp.federator.processor;

import static uk.gov.dbt.ndtp.federator.utils.HeaderUtils.selectHeaders;

import com.google.protobuf.ByteString;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.secure.agent.payloads.RdfPayload;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.serializers.RdfPayloadSerializer;

/**
 * Processes a Kafka event message and sends it to the server call stream observer
 * @see StreamObservable
 */
public class RdfKafkaEventMessageProcessor implements MessageProcessor<KafkaEvent<String, RdfPayload>> {

    public static final Logger LOGGER = LoggerFactory.getLogger("RdfKafkaEventMessagePostProcessor");

    private final StreamObservable serverCallStreamObserver;
    private final RdfPayloadSerializer serializer;
    private final Set<String> sharedHeaders;

    public RdfKafkaEventMessageProcessor(StreamObservable serverCallStreamObserver, Set<String> sharedHeaders) {
        this.serverCallStreamObserver = serverCallStreamObserver;
        serializer = new RdfPayloadSerializer();
        this.sharedHeaders = sharedHeaders;
    }

    @Override
    public void process(KafkaEvent<String, RdfPayload> kafkaEvent) {
        try {
            LOGGER.debug("Processing message");
            String topic = kafkaEvent.getConsumerRecord().topic();
            long offset = kafkaEvent.getConsumerRecord().offset();
            ByteString byteStringValue = ByteString.copyFrom(serializer.serialize(topic, kafkaEvent.value()));
            ByteString byteStringKey = ByteString.copyFrom(
                    (null != kafkaEvent.key())
                            ? kafkaEvent.key().getBytes()
                            : ("Missing Key - " + offset + " " + topic).getBytes());
            KafkaByteBatch response = KafkaByteBatch.newBuilder()
                    .setTopic(topic)
                    .setOffset(offset)
                    .setValue(byteStringValue)
                    .setKey(byteStringKey)
                    .addAllShared(selectHeaders(kafkaEvent.headers(), sharedHeaders))
                    .build();

            serverCallStreamObserver.onNext(response);
        } catch (Exception e) {
            LOGGER.error("Exception encountered processing message", e);
            serverCallStreamObserver.onError(e);
        }
    }
}
