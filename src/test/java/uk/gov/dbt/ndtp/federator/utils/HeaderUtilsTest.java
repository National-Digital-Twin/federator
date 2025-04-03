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
import static uk.gov.dbt.ndtp.federator.utils.HeaderUtils.getSecurityLabelFromHeaders;
import static uk.gov.dbt.ndtp.federator.utils.HeaderUtils.selectHeaders;
import static uk.gov.dbt.ndtp.secure.agent.sources.IANodeHeaders.SECURITY_LABEL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.grpc.Headers;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;

public class HeaderUtilsTest {

    @Test
    void testGetSecurityLabelFromHeaders() {

        List<Header> headers = new ArrayList<>();
        assertEquals("", getSecurityLabelFromHeaders(headers.stream()));
        headers.add(new Header("", ""));
        assertEquals("", getSecurityLabelFromHeaders(headers.stream()));
        headers.add(new Header("Rubbish", "Rubbish"));
        assertEquals("", getSecurityLabelFromHeaders(headers.stream()));
        headers.add(new Header("", "Rubbish"));
        assertEquals("", getSecurityLabelFromHeaders(headers.stream()));
        headers.add(new Header("Rubbish", ""));
        assertEquals("", getSecurityLabelFromHeaders(headers.stream()));
        headers.add(new Header(SECURITY_LABEL, ""));
        assertEquals("", getSecurityLabelFromHeaders(headers.stream()));
        headers.add(new Header(SECURITY_LABEL, "SOME RANDOM STUFF"));
        // picks the first
        assertEquals("", getSecurityLabelFromHeaders(headers.stream()));
        // Clear the list down
        headers.clear();
        headers.add(new Header("Rubbish", "Rubbish"));
        headers.add(new Header(SECURITY_LABEL, "SOME RANDOM STUFF"));
        assertEquals("SOME RANDOM STUFF", getSecurityLabelFromHeaders(headers.stream()));
        headers.add(new Header("more", "Rubbish"));
        assertEquals("SOME RANDOM STUFF", getSecurityLabelFromHeaders(headers.stream()));
    }

    @Test
    void testSelectHeaders() {
        Set<String> headerKeys = new HashSet<>();
        List<Header> headers = new ArrayList<>();
        List<Headers> headersExpected = new ArrayList<>();

        assertEquals(headersExpected, selectHeaders(headers.stream(), headerKeys));
        headers.add(new Header("", ""));
        assertEquals(headersExpected, selectHeaders(headers.stream(), headerKeys));
        headers.add(new Header("Rubbish", "Rubbish"));
        assertEquals(headersExpected, selectHeaders(headers.stream(), headerKeys));

        headers.clear();
        headerKeys.clear();
        headersExpected.clear();

        headerKeys.add("missing-header");
        headerKeys.add("key-2");
        headerKeys.add("key-4");

        headers.add(new Header("Rubbish-1", "Rubbish-1"));
        headers.add(new Header("key-1", "value-1"));
        headers.add(new Header("Rubbish-2", "Rubbish-2"));
        headers.add(new Header("key-2", "value-2"));
        headers.add(new Header("key-3", "value-3"));
        headers.add(new Header("Rubbish-3", "Rubbish-3"));
        headers.add(new Header("Rubbish-4", "Rubbish-4"));
        headers.add(new Header("key-4", "value-4"));

        headersExpected.add(
                Headers.newBuilder().setKey("key-2").setValue("value-2").build());
        headersExpected.add(
                Headers.newBuilder().setKey("key-4").setValue("value-4").build());

        assertEquals(headersExpected, selectHeaders(headers.stream(), headerKeys));
    }
}
