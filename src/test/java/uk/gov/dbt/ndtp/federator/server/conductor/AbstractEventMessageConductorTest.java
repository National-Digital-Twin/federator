/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.conductor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.model.dto.AttributesDTO;
import uk.gov.dbt.ndtp.federator.server.consumer.MessageConsumer;
import uk.gov.dbt.ndtp.federator.server.processor.MessageProcessor;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.Header;

class AbstractEventMessageConductorTest {

    private MessageConsumer<Event<String, String>> mockConsumer;
    private MessageProcessor<Event<String, String>> mockProcessor;
    private List<AttributesDTO> filterAttributes;
    private AbstractEventMessageConductor<String, String> conductor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockConsumer = mock(MessageConsumer.class);
        mockProcessor = mock(MessageProcessor.class);
        filterAttributes = Collections.emptyList();
        conductor = new AbstractEventMessageConductor<String, String>(mockConsumer, filterAttributes, mockProcessor) {};
    }

    @Test
    void testProcessMessage_NullEvent() {
        when(mockConsumer.getNextMessage()).thenReturn(null);

        conductor.processMessage();

        verify(mockProcessor, never()).process(any());
    }

    @Test
    void testProcessMessage_WithEvent() {
        Event<String, String> mockEvent = mock(Event.class);
        when(mockEvent.key()).thenReturn("testKey");
        Header header = mock(Header.class);
        when(header.toString()).thenReturn("header1");
        when(mockEvent.headers()).thenReturn(Stream.of(header));
        when(mockConsumer.getNextMessage()).thenReturn(mockEvent);

        conductor.processMessage();

        verify(mockProcessor).process(mockEvent);
    }

    @Test
    void testProcessMessages() throws Exception {
        when(mockConsumer.stillAvailable()).thenReturn(true, false);
        Event<String, String> mockEvent = mock(Event.class);
        when(mockEvent.key()).thenReturn("testKey");
        when(mockEvent.headers()).thenReturn(Stream.empty());
        when(mockConsumer.getNextMessage()).thenReturn(mockEvent);

        conductor.processMessages();

        verify(mockProcessor, times(1)).process(mockEvent);
        verify(mockConsumer, times(2)).stillAvailable();
    }

    @Test
    void testClose() {
        conductor.close();
        verify(mockConsumer).close();
        verify(mockProcessor).close();
    }

    @Test
    void testClose_WithExceptions() {
        doThrow(new RuntimeException("Consumer Close Error")).when(mockConsumer).close();
        doThrow(new RuntimeException("Processor Close Error"))
                .when(mockProcessor)
                .close();

        // Should not throw exception
        assertDoesNotThrow(() -> conductor.close());

        verify(mockConsumer).close();
        verify(mockProcessor).close();
    }
}
