/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExceptionsTest {

    @Test
    void testConfigurationException() {
        ConfigurationException ex1 = new ConfigurationException("msg");
        assertEquals("msg", ex1.getMessage());

        Throwable cause = new RuntimeException("cause");
        ConfigurationException ex2 = new ConfigurationException("msg", cause);
        assertEquals("msg", ex2.getMessage());
        assertEquals(cause, ex2.getCause());

        ConfigurationException ex3 = new ConfigurationException("prop", "expected");
        assertTrue(ex3.getMessage().contains("prop"));
        assertTrue(ex3.getMessage().contains("expected"));

        ConfigurationException.ConfigurationParsingException ex4 = new ConfigurationException.ConfigurationParsingException("msg", cause);
        assertEquals("msg", ex4.getMessage());
        assertEquals(cause, ex4.getCause());

        ConfigurationException.ConfigurationValidationException ex5 = new ConfigurationException.ConfigurationValidationException("msg");
        assertEquals("msg", ex5.getMessage());
    }

    @Test
    void testOtherExceptions() {
        assertNotNull(new AccessDeniedException("msg").getMessage());
        assertNotNull(new AccessDeniedException("msg", new RuntimeException()).getMessage());
        assertNotNull(new AesCryptographicOperationException("msg", new RuntimeException()).getMessage());
        assertNotNull(new ClientGRPCJobException("msg", new RuntimeException()).getMessage());
        assertNotNull(new FederatorSslException("msg").getMessage());
        assertNotNull(new FederatorSslException("msg", new RuntimeException()).getMessage());
        assertNotNull(new FederatorTokenException("msg").getMessage());
        assertNotNull(new FederatorTokenException("msg", new RuntimeException()).getMessage());
        assertNotNull(new FileAssemblyException("msg").getMessage());
        assertNotNull(new FileFetcherException("msg").getMessage());
        assertNotNull(new FileFetcherException("msg", new RuntimeException()).getMessage());
        assertNotNull(new FilterCreationException("msg").getMessage());
        assertNotNull(new FilterCreationException("msg", new RuntimeException()).getMessage());
        assertNotNull(new LabelException("msg").getMessage());
        assertNotNull(new MessageProcessingException("msg", new RuntimeException()).getMessage());
        assertNotNull(new MessageProcessingException(new RuntimeException()).getCause());
        assertNotNull(new RetryableException("msg", new RuntimeException()).getMessage());
        assertNotNull(new RetryableException(new RuntimeException()).getCause());
        assertNotNull(new TopicInvalidException("msg").getMessage());
    }
}
