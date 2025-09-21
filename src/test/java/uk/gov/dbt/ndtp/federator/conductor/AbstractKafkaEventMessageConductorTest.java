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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.processor.MessageProcessor;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

/**
 * Unit tests for AbstractKafkaEventMessageConductor focusing on header-based filtering logic in isEventAllowed().
 */
class AbstractKafkaEventMessageConductorTest {

    private static Header header(String k, String v) {
        Header h = mock(Header.class);
        when(h.key()).thenReturn(k);
        when(h.value()).thenReturn(v);
        return h;
    }

    private static KafkaEvent<String, String> eventWithHeaders(Header... headers) {
        @SuppressWarnings("unchecked")
        KafkaEvent<String, String> evt = (KafkaEvent<String, String>) mock(KafkaEvent.class);
        when(evt.headers()).thenReturn(Stream.of(headers));
        return evt;
    }

    @Test
    void isEventAllowed_returnsTrue_whenNoFilterAttributesConfigured() {
        TestConductor cut = new TestConductor(null); // null/empty => allow all
        KafkaEvent<String, String> evt = eventWithHeaders(header("any", "value"));
        assertTrue(cut.callIsAllowed(evt));

        cut = new TestConductor(List.of());
        assertTrue(cut.callIsAllowed(evt));
    }

    @Test
    void isEventAllowed_true_whenSingleAttributeMatches_caseInsensitiveKeyAndValue() {
        // given filter requires X:Y, but headers provide x:y and value in different casing
        AttributesDTO attr = AttributesDTO.builder()
                .name("X-Key")
                .value("Alpha")
                .type("String")
                .build();
        TestConductor cut = new TestConductor(List.of(attr));
        KafkaEvent<String, String> evt =
                eventWithHeaders(header("x-key".toUpperCase(Locale.ROOT), "alpha".toLowerCase(Locale.ROOT)));
        // when/then
        assertTrue(cut.callIsAllowed(evt));
    }

    @Test
    void isEventAllowed_false_whenSingleAttributeValueMismatch() {
        AttributesDTO attr =
                AttributesDTO.builder().name("tenant").value("alpha").build();
        TestConductor cut = new TestConductor(List.of(attr));
        KafkaEvent<String, String> evt = eventWithHeaders(header("tenant", "beta"));
        assertFalse(cut.callIsAllowed(evt));
    }

    @Test
    void isEventAllowed_false_whenRequiredHeaderMissing() {
        AttributesDTO attr = AttributesDTO.builder().name("region").value("eu").build();
        TestConductor cut = new TestConductor(List.of(attr));
        KafkaEvent<String, String> evt = eventWithHeaders(header("tenant", "alpha"));
        assertFalse(cut.callIsAllowed(evt));
    }

    @Test
    void isEventAllowed_true_whenAllMultipleAttributesMatch_ANDSemantics() {
        List<AttributesDTO> attrs = List.of(
                AttributesDTO.builder().name("tenant").value("alpha").build(),
                AttributesDTO.builder().name("sensitivity").value("public").build());
        TestConductor cut = new TestConductor(attrs);
        KafkaEvent<String, String> evt = eventWithHeaders(header("tenant", "ALPHA"), header("sensitivity", "Public"));
        assertTrue(cut.callIsAllowed(evt));
    }

    @Test
    void isEventAllowed_false_whenAnyMultipleAttributeMismatch_ANDSemantics() {
        List<AttributesDTO> attrs = List.of(
                AttributesDTO.builder().name("tenant").value("alpha").build(),
                AttributesDTO.builder().name("sensitivity").value("public").build());
        TestConductor cut = new TestConductor(attrs);
        KafkaEvent<String, String> evt = eventWithHeaders(header("tenant", "alpha"), header("sensitivity", "private"));
        assertFalse(cut.callIsAllowed(evt));
    }

    @Test
    void isEventAllowed_ignoresNullAttributeEntries_butHonoursValidOnes() {
        java.util.ArrayList<AttributesDTO> attrs = new java.util.ArrayList<>();
        attrs.add(null); // should be ignored
        attrs.add(AttributesDTO.builder().name("env").value("prod").build());
        TestConductor cut = new TestConductor(attrs);
        KafkaEvent<String, String> evt = eventWithHeaders(header("ENV", "PROD"));
        assertTrue(cut.callIsAllowed(evt));
    }

    @Test
    void isEventAllowed_false_whenAttributeHasNullNameOrNullValue() {
        // null name
        List<AttributesDTO> attrs1 =
                List.of(AttributesDTO.builder().name(null).value("x").build());
        TestConductor cut1 = new TestConductor(attrs1);
        KafkaEvent<String, String> evt1 = eventWithHeaders(header("k", "v"));
        assertFalse(cut1.callIsAllowed(evt1));

        // null value
        List<AttributesDTO> attrs2 =
                List.of(AttributesDTO.builder().name("k").value(null).build());
        TestConductor cut2 = new TestConductor(attrs2);
        KafkaEvent<String, String> evt2 = eventWithHeaders(header("k", "v"));
        assertFalse(cut2.callIsAllowed(evt2));
    }

    @Test
    void isEventAllowed_usesFirstHeaderValue_whenDuplicateKeysPresent() {
        // Given duplicate headers for the same key, the first value should win
        List<AttributesDTO> attrs =
                List.of(AttributesDTO.builder().name("flag").value("on").build());
        TestConductor cut = new TestConductor(attrs);
        KafkaEvent<String, String> evt = eventWithHeaders(
                header("flag", "on"), header("FLAG", "OFF")); // duplicate with different case and different value
        assertTrue(cut.callIsAllowed(evt));

        // If the first is non-matching and a later one matches, it should still be denied
        KafkaEvent<String, String> evt2 = eventWithHeaders(header("flag", "OFF"), header("FLAG", "ON"));
        assertFalse(cut.callIsAllowed(evt2));
    }

    // Test helper: minimal concrete subclass to expose isEventAllowed for testing
    private static class TestConductor extends AbstractKafkaEventMessageConductor<String, String> {
        public TestConductor(List<AttributesDTO> filterAttributes) {
            super(
                    (MessageConsumer<KafkaEvent<String, String>>) null,
                    (MessageProcessor<KafkaEvent<String, String>>) null,
                    filterAttributes);
        }

        public boolean callIsAllowed(KafkaEvent<String, String> event) {
            return isEventAllowed(event);
        }
    }
}
