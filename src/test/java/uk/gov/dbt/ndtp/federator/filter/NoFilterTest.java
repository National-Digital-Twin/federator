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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

public class NoFilterTest {

    @Test
    public void test_AlwaysReturnFalse() {
        // given
        NoFilter<TestMessageType> cut = new NoFilter<>();
        // when
        TestMessageType messageType = new TestMessageType();
        // then
        assertFalse(cut.filterOut(messageType));
    }

    @Test
    public void test_AlwaysReturnFalse_evenNull() {
        // given
        NoFilter<TestMessageType> cut = new NoFilter<>();
        // when
        // then
        assertFalse(cut.filterOut(null));
    }

    @Test
    public void test_close_noop() {
        // given
        NoFilter<TestMessageType> cut = new NoFilter<>();
        // when
        // then
        assertDoesNotThrow(cut::close);
    }

    private static class TestMessageType {}
}
