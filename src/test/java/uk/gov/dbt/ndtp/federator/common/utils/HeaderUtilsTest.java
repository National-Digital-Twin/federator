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
package uk.gov.dbt.ndtp.federator.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.exceptions.LabelException;

class HeaderUtilsTest {

    @Test
    void parses_single_pair_A_equals_B() throws LabelException {
        Map<String, String> map = HeaderUtils.getMapFromSecurityLabel("A=B");
        assertEquals(1, map.size());
        assertEquals("B", map.get("A"));
    }

    @Test
    void parses_single_pair_with_spaces_and_colon() throws LabelException {
        Map<String, String> map = HeaderUtils.getMapFromSecurityLabel("  nationality : uk  ");
        assertEquals(1, map.size());
        assertEquals("UK", map.get("NATIONALITY"));
    }

    @Test
    void parses_multiple_pairs() throws LabelException {
        Map<String, String> map = HeaderUtils.getMapFromSecurityLabel("A=B,C=D");
        assertEquals(2, map.size());
        assertEquals("B", map.get("A"));
        assertEquals("D", map.get("C"));
    }

    @Test
    void ignores_empty_segments_between_commas() throws LabelException {
        Map<String, String> map = HeaderUtils.getMapFromSecurityLabel(",A=B,,C=D,");
        assertEquals(2, map.size());
        assertTrue(map.containsKey("A"));
        assertTrue(map.containsKey("C"));
    }
}
