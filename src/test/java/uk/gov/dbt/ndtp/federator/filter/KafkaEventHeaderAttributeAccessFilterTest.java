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

package uk.gov.dbt.ndtp.federator.filter;

import static org.apache.kafka.common.record.TimestampType.*;
import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil.*;
import static uk.gov.dbt.ndtp.secure.agent.sources.IANodeHeaders.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;
import uk.gov.dbt.ndtp.secure.agent.payloads.RdfPayload;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

public class KafkaEventHeaderAttributeAccessFilterTest {

    @BeforeAll
    public static void setUpAllTests() {
        setUpProperties();
    }

    @AfterAll
    public static void clearDown() {
        clearProperties();
    }

    public static KafkaEvent<String, RdfPayload> getRdfPayloadKafkaEvent(
            String nationality, String randomClearance, String randomOrganisation) {
        String securityLabelValue = String.format(
                "nationality=%s,clearance=%s,organisation_type=%s", nationality, randomClearance, randomOrganisation);
        Header[] headerArray = {new RecordHeader(SECURITY_LABEL, securityLabelValue.getBytes((StandardCharsets.UTF_8)))
        };
        Headers headers = new RecordHeaders(headerArray);

        ConsumerRecord<String, RdfPayload> record = new ConsumerRecord<>(
                "topic", 1, 1L, 1L, NO_TIMESTAMP_TYPE, 0, 0, "key", null, headers, Optional.empty());

        KafkaEvent<String, RdfPayload> message = new KafkaEvent<>(record, null);
        return message;
    }

    @Test
    public void test_getMapFromSecurityLabel_null() throws LabelException {
        // given
        // when
        Map<String, String> actualMap = KafkaEventHeaderAttributeAccessFilter.getMapFromSecurityLabel(null);
        // then
        assertTrue(actualMap.isEmpty());
    }

    @Test
    public void test_getMapFromSecurityLabel_emptyString() throws LabelException {
        // given
        String emptyString = "";
        // when
        Map<String, String> actualMap = KafkaEventHeaderAttributeAccessFilter.getMapFromSecurityLabel(emptyString);
        // then
        assertTrue(actualMap.isEmpty());
    }

    @Test
    public void test_getMapFromSecurityLabel_correctFormat() throws LabelException {
        // given
        String emptyString = "key=value,key2:value2";
        // when
        Map<String, String> actualMap = KafkaEventHeaderAttributeAccessFilter.getMapFromSecurityLabel(emptyString);
        // then
        assertFalse(actualMap.isEmpty());
        assertEquals(actualMap.get("KEY"), "VALUE");
        assertEquals(actualMap.get("KEY2"), "VALUE2");
    }

    @Test
    public void test_getMapFromSecurityLabel_incorrectFormat() {
        // given
        String incorrectFormatString = "no_equals_or_colon_in_string";
        // when
        LabelException thrown = assertThrows(LabelException.class, () -> {
            KafkaEventHeaderAttributeAccessFilter.getMapFromSecurityLabel(incorrectFormatString);
        });
        // then
        String message = String.format("Cannot map security label: '%s'. Ignoring", incorrectFormatString);
        assertEquals(message, thrown.getMessage());
    }
}
