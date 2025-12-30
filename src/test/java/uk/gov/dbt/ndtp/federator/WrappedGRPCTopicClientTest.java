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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Random;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.client.grpc.GRPCTopicClient;

class WrappedGRPCTopicClientTest {

    private final GRPCTopicClient delegate = mock(GRPCTopicClient.class);

    private final WrappedGRPCClient underTest = new WrappedGRPCClient(delegate);

    @Test
    void close() throws Exception {
        underTest.close();

        verify(delegate).close();
    }

    @Test
    void getRedisPrefix() {
        String expected = RandomStringUtils.insecure().next(10);
        when(delegate.getRedisPrefix()).thenReturn(expected);

        String actual = underTest.getRedisPrefix();

        assertEquals(expected, actual);
        verify(delegate).getRedisPrefix();
    }

    @Test
    void processTopic() {
        String topic = RandomStringUtils.insecure().next(10);
        long offset = new Random().nextLong();
        underTest.processTopic(topic, offset);

        verify(delegate).processTopic(topic, offset);
    }

    @Test
    void testConnectivity() {
        underTest.testConnectivity();

        verify(delegate).testConnectivity();
    }
}
