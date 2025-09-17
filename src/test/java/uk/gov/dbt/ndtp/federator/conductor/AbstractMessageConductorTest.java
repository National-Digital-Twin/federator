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

package uk.gov.dbt.ndtp.federator.conductor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import uk.gov.dbt.ndtp.federator.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.exceptions.MessageProcessingException;
import uk.gov.dbt.ndtp.federator.filter.MessageFilter;
import uk.gov.dbt.ndtp.federator.processor.MessageProcessor;

class AbstractMessageConductorTest {

    @Mock
    private MessageConsumer<String> messageConsumer;

    @Mock
    private MessageProcessor<String> messageProcessor;

    @Mock
    private MessageFilter<String> messageFilter;

    private AbstractMessageConductor<String, String> conductor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        conductor = new ConcreteMessageConductor(messageConsumer, messageProcessor);
    }

    @Test
    void test_processMessages_shouldProcessMessagesCorrectly() throws Exception {
        when(messageConsumer.stillAvailable()).thenReturn(true, false);
        when(messageConsumer.getNextMessage()).thenReturn("message");

        conductor.processMessages();

        verify(messageConsumer).getNextMessage();
        verify(messageProcessor).process("message");
    }

    @Test
    void test_close_shouldHandleClosingResources() {
        conductor.close();

        verify(messageConsumer).close();
        verify(messageProcessor).close();
    }

    @Test
    void test_processMessages_shouldThrowMessageProcessingException() {
        when(messageConsumer.stillAvailable()).thenReturn(true);
        when(messageConsumer.getNextMessage()).thenReturn("message");
        doThrow(new RuntimeException("Processing failed"))
                .when(messageProcessor)
                .process(anyString());

        MessageProcessingException exception = assertThrows(MessageProcessingException.class, () -> {
            conductor.processMessages();
        });
        assertInstanceOf(RuntimeException.class, exception.getCause());
    }

    // Concrete subclass of AbstractMessageConductor for testing
    private static class ConcreteMessageConductor extends AbstractMessageConductor<String, String> {

        public ConcreteMessageConductor(MessageConsumer<String> consumer, MessageProcessor<String> postProcessor) {
            super(consumer, postProcessor, List.of());
        }

        // Implement the abstract method for testing
        @Override
        public void processMessage() {
            String message = messageConsumer.getNextMessage();
            if (message != null) {
                messageProcessor.process(message);
            }
        }
    }
}
