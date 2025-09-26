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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;

class SecurityLabelUtilTest {

    @ParameterizedTest(name = "[{index}] input=\"{0}\" -> expectedException={2}")
    @MethodSource("cases")
    void parse_parameterized(
            String input, Map<String, String> expectedMap, Class<? extends Exception> expectedException) {
        if (expectedException != null) {
            assertThrows(expectedException, () -> SecurityLabelUtil.parse(input));
            return;
        }
        Map<String, String> map;
        try {
            map = SecurityLabelUtil.parse(input).asMap();
        } catch (LabelException e) {
            // Should not happen for valid cases
            throw new AssertionError("Unexpected LabelException for input: " + input, e);
        }
        assertNotNull(map);
        assertEquals(expectedMap, map);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                // No entry -> empty map
                Arguments.of(null, Map.of(), null),
                Arguments.of("", Map.of(), null),
                Arguments.of("   \t  ", Map.of(), null),
                // Single entry
                Arguments.of("A=B", Map.of("A", "B"), null),
                Arguments.of(" nationality : uk ", Map.of("NATIONALITY", "UK"), null),
                // Many entries
                Arguments.of("A=B,C=D", Map.of("A", "B", "C", "D"), null),
                Arguments.of(", a=b , , c : d , ", Map.of("A", "B", "C", "D"), null),
                // Invalid segments -> exceptions
                Arguments.of("A", null, LabelException.class),
                Arguments.of("=B", null, LabelException.class),
                Arguments.of(":B", null, LabelException.class),
                Arguments.of("A=", null, LabelException.class),
                Arguments.of("A:", null, LabelException.class));
    }
}
