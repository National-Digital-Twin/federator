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

package uk.gov.dbt.ndtp.federator.consumer;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;

public class KafkaEventMessageConsumer<Key, Value> implements MessageConsumer<KafkaEvent<Key, Value>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaEventMessageConsumer.class);

    // Property key for inactivity timeout; if no messages are received for this duration, close the source
    private static final String CONSUMER_INACTIVITY_TIMEOUT = "consumer.inactivity.timeout";
    private static final String DEFAULT_INACTIVITY_TIMEOUT = "PT30S";
    private static final String KAFKA_POLL_DURATION_KEY = "kafka.pollDuration";
    private static final String DEFAULT_POLL_DURATION = "PT2S";

    private final KafkaEventSource<Key, Value> source;
    private final Duration pollDuration;
    private final Duration inactivityTimeout;
    private Instant lastMessageInstant;

    public KafkaEventMessageConsumer(
            Class<?> keyDeserializer, Class<?> valueDeserializer, String topic, long offset, String consumerGroup) {
        source = KafkaUtil.<Key, Value>getKafkaSourceBuilder()
                .keyDeserializer(keyDeserializer)
                .valueDeserializer(valueDeserializer)
                .topic(topic)
                .consumerGroup(consumerGroup)
                .readPolicy(KafkaUtil.getReadPolicy(offset))
                .build();
        pollDuration = PropertyUtil.getPropertyDurationValue(KAFKA_POLL_DURATION_KEY, DEFAULT_POLL_DURATION);
        inactivityTimeout =
                PropertyUtil.getPropertyDurationValue(CONSUMER_INACTIVITY_TIMEOUT, DEFAULT_INACTIVITY_TIMEOUT);
        lastMessageInstant = Instant.now();
    }

    @Override
    public boolean stillAvailable() {
        // If source already closed, short-circuit
        if (source.isClosed()) return false;
        // If we've been idle for longer than inactivityTimeout, close and return false
        if (Duration.between(lastMessageInstant, Instant.now()).compareTo(inactivityTimeout) >= 0) {
            LOGGER.info("Closing KafkaEventMessageConsumer due to inactivity timeout of {}", inactivityTimeout);
            try {
                source.close();
            } catch (Exception e) {
                LOGGER.debug("Error while closing Kafka source on inactivity", e);
            }
            return false;
        }
        return true;
    }

    @Override
    public KafkaEvent<Key, Value> getNextMessage() {
        KafkaEvent<Key, Value> event = (KafkaEvent<Key, Value>) source.poll(pollDuration);
        if (event != null) {
            lastMessageInstant = Instant.now();
        }
        return event;
    }

    @Override
    public void close() {
        source.close();
    }
}
