// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

/*
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

package uk.gov.dbt.ndtp.federator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.grpc.GRPCClient;
import uk.gov.dbt.ndtp.federator.utils.TestPropertyUtil;

class FederatorClientTest {

    @BeforeAll
    static void beforeAll() {
        TestPropertyUtil.clearProperties();
    }

    @AfterAll
    static void afterAll() {
        TestPropertyUtil.clearProperties();
    }

    @Test
    void run_happy() {
        GRPCClient noopClient = mock(GRPCClient.class);

        when(noopClient.getRedisPrefix()).thenReturn("prefix");
        when(noopClient.obtainTopics()).thenReturn(Collections.emptyList());

        FederatorClient underTest = new FederatorClient(config -> noopClient);

        assertDoesNotThrow(underTest::run);
    }

    @Test
    void run_handles_error_when_creating_grpc_client() {
        FederatorClient underTest = new FederatorClient(config -> {
            throw new RuntimeException("oops");
        });

        assertDoesNotThrow(underTest::run);
    }
}
