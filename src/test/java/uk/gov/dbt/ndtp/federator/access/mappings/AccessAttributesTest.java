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

package uk.gov.dbt.ndtp.federator.access.mappings;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

class AccessAttributesTest {
    private static final String RANDOM_CLEARANCE = RandomStringUtils.random(6, true, true);
    private static final String RANDOM_ORGANISATION = RandomStringUtils.random(6, true, false);
    private static final String RANDOM_NATIONALITY = RandomStringUtils.random(3, true, false);

    @Test
    void test_noArgs_constructor() {
        AccessAttributes accessAttributes = new AccessAttributes();

        assertNull(accessAttributes.getNationality());
        assertNull(accessAttributes.getClearance());
        assertNull(accessAttributes.getOrganisation_type());
    }

    @Test
    void test_allArgs_constructor() {
        AccessAttributes accessAttributes =
                new AccessAttributes(RANDOM_NATIONALITY, RANDOM_CLEARANCE, RANDOM_ORGANISATION);

        assertEquals(RANDOM_NATIONALITY, accessAttributes.getNationality());
        assertEquals(RANDOM_CLEARANCE, accessAttributes.getClearance());
        assertEquals(RANDOM_ORGANISATION, accessAttributes.getOrganisation_type());
    }

    @Test
    void test_builder() {
        AccessAttributes accessAttributes = AccessAttributes.builder()
                .nationality(RANDOM_NATIONALITY)
                .clearance(RANDOM_CLEARANCE)
                .organisation_type(RANDOM_ORGANISATION)
                .build();

        // Assert that the values are correctly set using the builder
        assertEquals(RANDOM_NATIONALITY, accessAttributes.getNationality());
        assertEquals(RANDOM_CLEARANCE, accessAttributes.getClearance());
        assertEquals(RANDOM_ORGANISATION, accessAttributes.getOrganisation_type());
    }

    @Test
    void test_setters_and_getters() {
        AccessAttributes accessAttributes = new AccessAttributes();

        accessAttributes.setNationality(RANDOM_NATIONALITY);
        accessAttributes.setClearance(RANDOM_CLEARANCE);
        accessAttributes.setOrganisation_type(RANDOM_ORGANISATION);

        assertEquals(RANDOM_NATIONALITY, accessAttributes.getNationality());
        assertEquals(RANDOM_CLEARANCE, accessAttributes.getClearance());
        assertEquals(RANDOM_ORGANISATION, accessAttributes.getOrganisation_type());
    }

    @Test
    void test_static_constants() {
        assertEquals("NATIONALITY", AccessAttributes.NATIONALITY_ATTRIBUTE);
        assertEquals("CLEARANCE", AccessAttributes.CLEARANCE_ATTRIBUTE);
        assertEquals("ORGANISATION_TYPE", AccessAttributes.ORGANISATION_TYPE_ATTRIBUTE);
    }
}
