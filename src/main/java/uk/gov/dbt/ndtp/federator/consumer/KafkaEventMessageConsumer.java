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

import static uk.gov.dbt.ndtp.federator.utils.KafkaUtil.KAFKA_POLL_DURATION;
import static uk.gov.dbt.ndtp.federator.utils.KafkaUtil.PT2S;

import java.time.Duration;
import uk.gov.dbt.ndtp.federator.utils.KafkaUtil;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;

public class KafkaEventMessageConsumer<Key, Value> implements MessageConsumer<KafkaEvent<Key, Value>> {

    private final KafkaEventSource<Key, Value> source;
    private final Duration pollDuration;

    public KafkaEventMessageConsumer(
            Class<?> keyDeserializer, Class<?> valueDeserializer, String topic, long offset, String consumerGroup) {
        source = KafkaUtil.<Key, Value>getKafkaSourceBuilder()
                .keyDeserializer(keyDeserializer)
                .valueDeserializer(valueDeserializer)
                .topic(topic)
                .consumerGroup(consumerGroup)
                .readPolicy(KafkaUtil.getReadPolicy(offset))
                .build();
        pollDuration = PropertyUtil.getPropertyDurationValue(KAFKA_POLL_DURATION, PT2S);
    }

    @Override
    public boolean stillAvailable() {
        return !source.isClosed();
    }

    @Override
    public KafkaEvent<Key, Value> getNextMessage() {
        return (KafkaEvent<Key, Value>) source.poll(pollDuration);
    }

    @Override
    public void close() {
        source.close();
    }
}
