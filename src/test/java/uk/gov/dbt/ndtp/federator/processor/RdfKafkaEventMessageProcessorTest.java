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

package uk.gov.dbt.ndtp.federator.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.grpc.LimitedServerCallStreamObserver;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.grpc.KafkaByteBatch;
import uk.gov.dbt.ndtp.secure.agent.payloads.RdfPayload;
import uk.gov.dbt.ndtp.secure.agent.sources.kafka.KafkaEvent;

public class RdfKafkaEventMessageProcessorTest {

    final StreamObservable mockObserver = mock(LimitedServerCallStreamObserver.class);
    final Set<String> sharedHeaders = Set.of();
    final RdfKafkaEventMessageProcessor cut = new RdfKafkaEventMessageProcessor(mockObserver, sharedHeaders);

    @Test
    public void test_process_happyPath() {
        // given
        KafkaEvent<String, RdfPayload> message =
                new KafkaEvent<>(new ConsumerRecord<>("topic", 1, 1, "key", null), null);
        // when
        cut.process(message);
        // then
        verify(mockObserver).onNext(any(KafkaByteBatch.class));
    }

    @Test
    public void test_process_nullEvent() {
        // given
        // when
        cut.process(null);
        // then
        verify(mockObserver).onError(any(NullPointerException.class));
    }

    @Test
    public void test_process_missingKey() {
        // given
        KafkaEvent<String, RdfPayload> message =
                new KafkaEvent<>(new ConsumerRecord<>("topic", 1, 1, null, null), null);

        // when
        cut.process(message);
        // then
        verify(mockObserver).onNext(any(KafkaByteBatch.class));
    }
}
