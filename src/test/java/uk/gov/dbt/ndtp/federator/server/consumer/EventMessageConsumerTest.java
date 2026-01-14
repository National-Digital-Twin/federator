/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.EventSource;

class EventMessageConsumerTest {

    private EventSource<String, String> mockSource;
    private EventMessageConsumer<String, String> consumer;
    private final Duration pollDuration = Duration.ofSeconds(1);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockSource = mock(EventSource.class);
        consumer = new EventMessageConsumer<>(mockSource, pollDuration);
    }

    @Test
    void testStillAvailable_WhenNotClosed() {
        when(mockSource.isClosed()).thenReturn(false);
        assertTrue(consumer.stillAvailable());
    }

    @Test
    void testStillAvailable_WhenClosed() {
        when(mockSource.isClosed()).thenReturn(true);
        assertFalse(consumer.stillAvailable());
    }

    @Test
    void testGetNextMessage() {
        Event<String, String> mockEvent = mock(Event.class);
        when(mockSource.poll(pollDuration)).thenReturn(mockEvent);

        Event<String, String> result = consumer.getNextMessage();

        assertEquals(mockEvent, result);
        verify(mockSource).poll(pollDuration);
    }

    @Test
    void testClose() {
        consumer.close();
        verify(mockSource).close();
    }
}
