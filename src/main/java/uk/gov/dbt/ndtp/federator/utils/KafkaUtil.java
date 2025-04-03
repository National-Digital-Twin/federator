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

package uk.gov.dbt.ndtp.federator.utils;

import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.policies.KafkaReadPolicies;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.policies.KafkaReadPolicy;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.sinks.KafkaSink;

/**
 * Utility class for Kafka.
 * <p>
 *   This class provides utility methods for Kafka.
 *   It is a singleton class and should not be instantiated.
 *   It provides methods to get Kafka source and sink builders.
 *   It also provides methods to get Kafka read policy and duration.
 *   It also provides methods to get Kafka sink.
 *   It also provides methods to get Kafka source builder.
 */
public class KafkaUtil {

    // Get all the keys up top as static strings.
    public static final String KAFKA_DEFAULT_KEY_DESERIALIZER_CLASS = "kafka.defaultKeyDeserializerClass";
    public static final String KAFKA_DEFAULT_VALUE_DESERIALIZER_CLASS = "kafka.defaultValueDeserializerClass";
    public static final String KAFKA_SENDER_DEFAULT_KEY_SERIALIZER_CLASS = "kafka.sender.defaultKeySerializerClass";
    public static final String KAFKA_SENDER_DEFAULT_VALUE_SERIALIZER_CLASS = "kafka.sender.defaultValueSerializerClass";
    public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrapServers";
    public static final String KAFKA_CONSUMER_GROUP = "kafka.consumerGroup";
    public static final String KAFKA_POLLRECORDS = "kafka.pollRecords";
    public static final String KAFKA_POLL_DURATION = "kafka.pollDuration";
    public static final String KAFKA_OFFSET = "kafka.offset";
    public static final String ZERO = "0";
    public static final String PT2S = "PT2S";
    private static final String ADDITIONAL_PROPERTIES = "kafka.additional.";

    public static final Logger LOGGER = LoggerFactory.getLogger("KafkaUtil");

    static {
        boolean isValidProperties = true;
        String bootstrapServer = PropertyUtil.getPropertyValue(KAFKA_BOOTSTRAP_SERVERS);
        if (null == bootstrapServer || bootstrapServer.isEmpty()) {
            LOGGER.error("KafkaUtil property '{}' not correctly set", KAFKA_BOOTSTRAP_SERVERS);
            isValidProperties = false;
        }
        String consumerGroup = PropertyUtil.getPropertyValue(KAFKA_CONSUMER_GROUP);
        if (null == consumerGroup || consumerGroup.isEmpty()) {
            LOGGER.error("KafkaUtil property '{}' not correctly set", KAFKA_CONSUMER_GROUP);
            isValidProperties = false;
        }
        if (!isValidProperties) {
            LOGGER.error("Problem with the properties for KafkaUtil. Exit early.");
            throw new RuntimeException("Problem with the properties for KafkaUtil. Exit early.");
        }
    }

    private KafkaUtil() {}

    public static <Key, Value> KafkaEventSource.Builder<Key, Value> getKafkaSourceBuilder() {
        KafkaEventSource.Builder<Key, Value> builder = KafkaEventSource.<Key, Value>create()
                .keyDeserializer(PropertyUtil.getPropertyValue(KAFKA_DEFAULT_KEY_DESERIALIZER_CLASS))
                .valueDeserializer(PropertyUtil.getPropertyValue(KAFKA_DEFAULT_VALUE_DESERIALIZER_CLASS))
                .bootstrapServers(PropertyUtil.getPropertyValue(KAFKA_BOOTSTRAP_SERVERS))
                .consumerGroup(PropertyUtil.getPropertyValue(KAFKA_CONSUMER_GROUP))
                .maxPollRecords(PropertyUtil.getPropertyIntValue(KAFKA_POLLRECORDS))
                .readPolicy(getReadPolicy());

        getAdditionalProperties().ifPresent(builder::consumerConfig);

        return builder;
    }

    public static <Key, Value> KafkaReadPolicy<Key, Value> getReadPolicy() {
        long offset = PropertyUtil.getPropertyLongValue(KAFKA_OFFSET, ZERO);
        return getReadPolicy(offset);
    }

    public static <Key, Value> KafkaReadPolicy<Key, Value> getReadPolicy(long offset) {
        return (offset == 0L ? KafkaReadPolicies.fromBeginning() : KafkaReadPolicies.fromOffsets(null, offset));
    }

    public static Duration getDuration() {
        return PropertyUtil.getPropertyDurationValue(KAFKA_POLL_DURATION, PT2S);
    }

    public static <Key, Value> KafkaSink.KafkaSinkBuilder<Key, Value> getKafkaSinkBuilder() {
        KafkaSink.KafkaSinkBuilder<Key, Value> builder = KafkaSink.<Key, Value>create()
                .keySerializer(PropertyUtil.getPropertyValue(KAFKA_SENDER_DEFAULT_KEY_SERIALIZER_CLASS))
                .valueSerializer(PropertyUtil.getPropertyValue(KAFKA_SENDER_DEFAULT_VALUE_SERIALIZER_CLASS))
                .bootstrapServers(PropertyUtil.getPropertyValue(KAFKA_BOOTSTRAP_SERVERS));

        getAdditionalProperties().ifPresent(builder::producerConfig);

        return builder;
    }

    public static <Key, Value> KafkaSink<Key, Value> getKafkaSink(String topic) {
        return KafkaUtil.<Key, Value>getKafkaSinkBuilder().topic(topic).build();
    }

    private static Optional<Properties> getAdditionalProperties() {
        Properties additional = PropertyUtil.getByPrefix(ADDITIONAL_PROPERTIES);
        if (additional.isEmpty()) {
            return Optional.empty();
        }
        Properties properties = new Properties();

        additional.forEach((key, value) -> {
            String processedKey = ((String) key).substring(ADDITIONAL_PROPERTIES.length());
            properties.setProperty(processedKey, (String) value);
        });
        return Optional.of(properties);
    }
}
