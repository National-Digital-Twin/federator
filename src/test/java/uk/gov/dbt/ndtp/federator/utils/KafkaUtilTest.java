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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil.clearProperties;
import static uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil.setUpProperties;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.AbstractKafkaEventSourceBuilder;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEventSource;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.policies.KafkaReadPolicy;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.policies.automatic.AutoFromBeginning;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.policies.automatic.AutoFromOffset;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.sinks.KafkaSink;

class KafkaUtilTest {
    @BeforeEach
    void setUpTests() {
        setUpProperties();
    }

    @AfterEach
    void clearDown() {
        clearProperties();
    }

    @Test
    void test_getReadPolicy_ifOffsetZero_readFromBeginning() {
        // given
        KafkaReadPolicy<String, String> expectedPolicy = new AutoFromBeginning<>();
        // when
        KafkaReadPolicy<String, String> actualPolicy = KafkaUtil.getReadPolicy(0L);
        // then
        assertEquals(expectedPolicy.getClass(), actualPolicy.getClass());
    }

    @Test
    void test_getReadPolicy_ifOffsetNonZero_readFromOffset() {
        // given
        KafkaReadPolicy<String, String> expectedPolicy = new AutoFromOffset<>();
        // when
        KafkaReadPolicy<String, String> actualPolicy = KafkaUtil.getReadPolicy(5L);
        // then
        assertEquals(expectedPolicy.getClass(), actualPolicy.getClass());
    }

    @Test
    void test_getReadPolicy_default_readFromBeginning() {
        // given
        KafkaReadPolicy<String, String> expectedPolicy = new AutoFromBeginning<>();
        // when
        KafkaReadPolicy<String, String> actualPolicy = KafkaUtil.getReadPolicy();
        // then
        assertEquals(expectedPolicy.getClass(), actualPolicy.getClass());
    }

    @Nested
    class KafkaSinkBuilderTest {

        private static final String BASE_PROPS =
                """
                    kafka.bootstrapServers=example.com:9092
                    kafka.consumerGroup=example
                    kafka.sender.defaultKeySerializerClass=org.apache.kafka.common.serialization.StringSerializer
                    kafka.sender.defaultValueSerializerClass=org.apache.kafka.common.serialization.StringSerializer
                    """;

        @Test
        void getKafkaSinkBuilder_populatesOverridableProperties()
                throws NoSuchFieldException, IllegalAccessException, IOException {
            TestPropertyUtil.clearProperties();

            Path propertiesLocation = FileUtils.createSelfDeletingTmpFile(null, null);

            String properties =
                    """
                    %s
                    kafka.additional.security.protocol=SASL_SSL
                    kafka.additional.sasl.mechanism=AWS_MSK_IAM
                    kafka.additional.sasl.jaas.config=software.amazon.msk.auth.iam.IAMLoginModule required;
                    kafka.additional.sasl.client.callback.handler.class=software.amazon.msk.auth.iam.IAMClientCallbackHandler
                    """
                            .formatted(BASE_PROPS);
            Files.writeString(propertiesLocation, properties);

            PropertyUtil.init(propertiesLocation.toFile());

            KafkaSink.KafkaSinkBuilder<?, ?> underTest = KafkaUtil.getKafkaSinkBuilder();

            Properties actual = getProperties(underTest);

            Properties expected = new Properties();
            expected.setProperty("security.protocol", "SASL_SSL");
            expected.setProperty("sasl.mechanism", "AWS_MSK_IAM");
            expected.setProperty("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
            expected.setProperty(
                    "sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");

            assertEquals(expected, actual);
        }

        @Test
        void getKafkaSinkBuilder_withoutOverridableProperties()
                throws NoSuchFieldException, IllegalAccessException, IOException {
            TestPropertyUtil.clearProperties();

            Path propertiesLocation = FileUtils.createSelfDeletingTmpFile(null, null);

            Files.writeString(propertiesLocation, BASE_PROPS);

            PropertyUtil.init(propertiesLocation.toFile());

            KafkaSink.KafkaSinkBuilder<?, ?> underTest = KafkaUtil.getKafkaSinkBuilder();

            Properties actual = getProperties(underTest);

            Properties expected = new Properties();

            assertEquals(expected, actual);
        }

        private static Properties getProperties(KafkaSink.KafkaSinkBuilder<?, ?> builder)
                throws NoSuchFieldException, IllegalAccessException {
            Field propertiesField = KafkaSink.KafkaSinkBuilder.class.getDeclaredField("properties");
            propertiesField.setAccessible(true);
            return (Properties) propertiesField.get(builder);
        }
    }

    @Nested
    class KafkaSourceBuilderTest {

        private static final String BASE_PROPS =
                """
                    kafka.bootstrapServers=example.com:9092
                    kafka.consumerGroup=example
                    kafka.defaultKeyDeserializerClass=org.apache.kafka.common.serialization.StringSerializer
                    kafka.defaultValueDeserializerClass=org.apache.kafka.common.serialization.StringSerializer
                    kafka.pollRecords=10
                    """;

        @Test
        void getKafkaSourceBuilder_populatesOverridableProperties()
                throws NoSuchFieldException, IllegalAccessException, IOException {
            TestPropertyUtil.clearProperties();

            Path propertiesLocation = FileUtils.createSelfDeletingTmpFile(null, null);

            String properties =
                    """
                    %s
                    kafka.additional.security.protocol=SASL_SSL
                    kafka.additional.sasl.mechanism=AWS_MSK_IAM
                    kafka.additional.sasl.jaas.config=software.amazon.msk.auth.iam.IAMLoginModule required;
                    kafka.additional.sasl.client.callback.handler.class=software.amazon.msk.auth.iam.IAMClientCallbackHandler
                    """
                            .formatted(BASE_PROPS);
            Files.writeString(propertiesLocation, properties);

            PropertyUtil.init(propertiesLocation.toFile());

            KafkaEventSource.Builder<?, ?> underTest = KafkaUtil.getKafkaSourceBuilder();

            Properties actual = getProperties(underTest);

            Properties expected = new Properties();
            expected.setProperty("security.protocol", "SASL_SSL");
            expected.setProperty("sasl.mechanism", "AWS_MSK_IAM");
            expected.setProperty("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");
            expected.setProperty(
                    "sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");

            assertEquals(expected, actual);
        }

        @Test
        void getKafkaSourceBuilder_withoutOverridableProperties()
                throws NoSuchFieldException, IllegalAccessException, IOException {
            TestPropertyUtil.clearProperties();

            Path propertiesLocation = FileUtils.createSelfDeletingTmpFile(null, null);

            Files.writeString(propertiesLocation, BASE_PROPS);

            PropertyUtil.init(propertiesLocation.toFile());

            KafkaEventSource.Builder<?, ?> underTest = KafkaUtil.getKafkaSourceBuilder();

            Properties actual = getProperties(underTest);

            Properties expected = new Properties();

            assertEquals(expected, actual);
        }

        private static Properties getProperties(KafkaEventSource.Builder<?, ?> builder)
                throws NoSuchFieldException, IllegalAccessException {
            Field propertiesField = AbstractKafkaEventSourceBuilder.class.getDeclaredField("properties");
            propertiesField.setAccessible(true);
            return (Properties) propertiesField.get(builder);
        }
    }
}
