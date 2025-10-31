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

import static org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.dbt.ndtp.secure.agent.sources.IANodeHeaders.SECURITY_LABEL;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.server.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Unit tests for AbstractKafkaEventMessageConductor focusing on header-based filtering logic in isEventAllowed().
 */
class AbstractKafkaEventMessageConductorTest {

    private KafkaEvent<String, String> eventWithSecLabel(String label) {
        RecordHeaders headers = new RecordHeaders(new RecordHeader[] {
            new RecordHeader(SECURITY_LABEL, label == null ? new byte[0] : label.getBytes(StandardCharsets.UTF_8))
        });
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(
                "topic", 1, 1L, 1L, NO_TIMESTAMP_TYPE, 0, 0, "key", null, headers, Optional.empty());
        return new KafkaEvent<>(consumerRecord, null);
    }

    private AttributesDTO attr(String name, String value) {
        return AttributesDTO.builder().name(name).value(value).build();
    }

    @Test
    void allows_when_no_filter_attributes_null() {
        TestConductor conductor = new TestConductor(null);
        KafkaEvent<String, String> event = eventWithSecLabel("NATIONALITY=UK,CLEARANCE=SECRET,ORGANISATION_TYPE=GOV");
        assertTrue(conductor.allowed(event));
    }

    @Test
    void allows_when_no_filter_attributes_empty() {
        TestConductor conductor = new TestConductor(new ArrayList<>());
        KafkaEvent<String, String> event = eventWithSecLabel("NATIONALITY=UK,CLEARANCE=SECRET,ORGANISATION_TYPE=GOV");
        assertTrue(conductor.allowed(event));
    }

    @Test
    void single_attribute_match_allows() {
        TestConductor conductor = new TestConductor(List.of(attr("nationality", "UK")));
        KafkaEvent<String, String> event = eventWithSecLabel("nationality=uk,clearance=secret,organisation_type=gov");
        assertTrue(conductor.allowed(event));
    }

    @Test
    void single_attribute_missing_denies() {
        TestConductor conductor = new TestConductor(List.of(attr("department", "BEIS")));
        KafkaEvent<String, String> event = eventWithSecLabel("nationality=uk,clearance=secret,organisation_type=gov");
        assertFalse(conductor.allowed(event));
    }

    @Test
    void single_attribute_value_mismatch_denies() {
        TestConductor conductor = new TestConductor(List.of(attr("clearance", "TOPSECRET")));
        KafkaEvent<String, String> event = eventWithSecLabel("nationality=uk,clearance=secret,organisation_type=gov");
        assertFalse(conductor.allowed(event));
    }

    @Test
    void multiple_attributes_all_match_allows() {
        TestConductor conductor = new TestConductor(List.of(attr("nationality", "UK"), attr("clearance", "SECRET")));
        KafkaEvent<String, String> event = eventWithSecLabel("NATIONALITY=UK,CLEARANCE=SECRET,ORGANISATION_TYPE=GOV");
        assertTrue(conductor.allowed(event));
    }

    @Test
    void multiple_attributes_one_missing_denies() {
        TestConductor conductor = new TestConductor(List.of(attr("nationality", "UK"), attr("department", "BEIS")));
        KafkaEvent<String, String> event = eventWithSecLabel("NATIONALITY=UK,CLEARANCE=SECRET,ORGANISATION_TYPE=GOV");
        assertFalse(conductor.allowed(event));
    }

    @Test
    void multiple_attributes_one_mismatch_denies() {
        TestConductor conductor = new TestConductor(List.of(attr("nationality", "UK"), attr("clearance", "TOPSECRET")));
        KafkaEvent<String, String> event = eventWithSecLabel("NATIONALITY=UK,CLEARANCE=SECRET,ORGANISATION_TYPE=GOV");
        assertFalse(conductor.allowed(event));
    }

    @Test
    void null_attribute_entries_ignored_others_match_allows() {
        List<AttributesDTO> attrs = new ArrayList<>();
        attrs.add(null);
        attrs.add(attr("nationality", "uk"));
        TestConductor conductor = new TestConductor(attrs);
        KafkaEvent<String, String> event = eventWithSecLabel("NATIONALITY=UK,CLEARANCE=SECRET,ORGANISATION_TYPE=GOV");
        assertTrue(conductor.allowed(event));
    }

    @Test
    void attribute_with_null_name_or_value_denies() {
        TestConductor conductor1 = new TestConductor(List.of(attr(null, "UK")));
        TestConductor conductor2 = new TestConductor(List.of(attr("nationality", null)));
        KafkaEvent<String, String> event = eventWithSecLabel("NATIONALITY=UK,CLEARANCE=SECRET,ORGANISATION_TYPE=GOV");
        assertFalse(conductor1.allowed(event));
        assertFalse(conductor2.allowed(event));
    }

    private static class TestConductor extends AbstractKafkaEventMessageConductor<String, String> {
        public TestConductor(List<AttributesDTO> filterAttributes) {
            super(Mockito.mock(MessageConsumer.class), Mockito.mock(MessageProcessor.class), filterAttributes);
        }

        public boolean allowed(KafkaEvent<String, String> event) {
            return isEventAllowed(event);
        }
    }
}
