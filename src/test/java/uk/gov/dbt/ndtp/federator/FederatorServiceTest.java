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
package uk.gov.dbt.ndtp.federator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.interfaces.StreamObservable;
import uk.gov.dbt.ndtp.federator.service.FederatorStreamService;
import uk.gov.dbt.ndtp.grpc.TopicRequest;

class FederatorServiceTest {

    @SuppressWarnings("unchecked")
    private static <T> void setPrivateField(Object target, String fieldName, T value) {
        try {
            java.lang.reflect.Field f = FederatorService.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void test_getKafkaConsumer_delegatesToKafkaStreamService() throws Exception {
        // Arrange
        Set<String> headers = Set.of("x-trace-id");
        FederatorService cut = new FederatorService(headers);

        @SuppressWarnings("rawtypes")
        FederatorStreamService mockKafka = mock(FederatorStreamService.class);
        setPrivateField(cut, "kafkaStreamService", mockKafka);

        TopicRequest request =
                TopicRequest.newBuilder().setTopic("topic-1").setOffset(5L).build();
        StreamObservable<uk.gov.dbt.ndtp.grpc.KafkaByteBatch> observable = mock(StreamObservable.class);

        // Act
        cut.getKafkaConsumer(request, observable);

        // Assert
        verify(mockKafka, times(1)).streamToClient(request, observable);
        verifyNoMoreInteractions(mockKafka);
    }

    @Test
    void test_getKafkaConsumer_propagatesInvalidTopicException() throws Exception {
        // Arrange
        FederatorService cut = new FederatorService(Set.of());
        @SuppressWarnings("rawtypes")
        FederatorStreamService mockKafka = mock(FederatorStreamService.class);
        setPrivateField(cut, "kafkaStreamService", mockKafka);

        TopicRequest request = TopicRequest.newBuilder().setTopic("forbidden").build();
        StreamObservable<uk.gov.dbt.ndtp.grpc.KafkaByteBatch> observable = mock(StreamObservable.class);

        doThrow(new InvalidTopicException("not allowed")).when(mockKafka).streamToClient(request, observable);

        // Act + Assert
        assertThrows(InvalidTopicException.class, () -> cut.getKafkaConsumer(request, observable));
    }

    @Test
    void test_getFileConsumer_delegatesToFileStreamService() {
        // Arrange
        FederatorService cut = new FederatorService(Set.of());
        @SuppressWarnings("rawtypes")
        FederatorStreamService mockFile = mock(FederatorStreamService.class);
        setPrivateField(cut, "fileStreamService", mockFile);

        uk.gov.dbt.ndtp.grpc.FileStreamRequest request = uk.gov.dbt.ndtp.grpc.FileStreamRequest.newBuilder()
                .setStartSequenceId(0L)
                .build();
        StreamObservable<uk.gov.dbt.ndtp.grpc.FileChunk> observable = mock(StreamObservable.class);

        // Act
        cut.getFileConsumer(request, observable);

        // Assert
        verify(mockFile, times(1)).streamToClient(request, observable);
        verifyNoMoreInteractions(mockFile);
    }
}
