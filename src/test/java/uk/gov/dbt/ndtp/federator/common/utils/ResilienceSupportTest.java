package uk.gov.dbt.ndtp.federator.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Properties;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import redis.clients.jedis.exceptions.JedisException;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

/**
 * ResilienceSupportTest
 */
public class ResilienceSupportTest {

    private static final String COMPONENT_NAME = "idp";
    private static final String OPERATION = "fetchToken";
    private static final String TARGET_ID = "client";
    private static final String BASE_MESSAGE = "Failed to fetch token after resilience protections for management node: 1234";

    private Supplier<String> supplier;

    private static void setupTestFileProperties() {
        Properties props = new Properties();
        props.setProperty("management.node.resilience.retry.maxAttempts", "1");
        props.setProperty("management.node.resilience.retry.maxBackOff", "PT0.2S");

        PropertyUtil propertyUtil = PropertyUtil.getInstance();
        propertyUtil.properties.putAll(props);
        PropertyUtil.overrideSystemProperties(propertyUtil.properties);
    }

    @BeforeAll
    static void initProperties() {
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
        PropertyUtil.init("test.properties");
        setupTestFileProperties();
    }

    @BeforeEach
    void setup() {
        supplier = Mockito.mock(Supplier.class);
    }

    @AfterAll
    static void tearDown() {
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
    }

    @Test
    void shouldEnrichAndRethrowRebuildableExceptionWithHttpTimeoutExceptionAsTheCause() {
        HttpTimeoutException originalException = new HttpTimeoutException("Error: request timed out!");
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (timeout while calling " + COMPONENT_NAME + " for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldEnrichAndRethrowRebuildableExceptionWithSocketTimeoutExceptionAsTheCause() {
        SocketTimeoutException originalException = new SocketTimeoutException("Error: request timed out!");
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (timeout while calling " + COMPONENT_NAME + " for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldEnrichAndRethrowRebuildableExceptionWithInterruptedIOExceptionAsTheCause() {
        InterruptedIOException originalException = new InterruptedIOException("Error: file not found!");
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (request was interrupted for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldEnrichAndRethrowRebuildableExceptionWithInterruptedExceptionAsTheCause() {
        InterruptedException originalException = new InterruptedException("Error: thread interrupted!");
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (request was interrupted for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldEnrichAndRethrowRebuildableExceptionWithIOExceptionAsTheCause() {
        IOException originalException = new IOException("Error: File not found!");
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (I/O error while calling " + COMPONENT_NAME + " for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldEnrichAndRethrowRebuildableExceptionWithJedisExceptionAsTheCause() {
        JedisException originalException = new JedisException("Error: key not found!");
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (redis cache failure for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldEnrichAndRethrowRebuildableExceptionWithUnmatchedCause() {
        RuntimeException originalException = new RuntimeException("unknown");
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (unexpected failure during " + OPERATION + " for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldRethrowNonRebuildableExceptionAsIs() {
        CallNotPermittedException callNotPermittedException = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults(COMPONENT_NAME));

        Mockito.when(supplier.get()).thenThrow(callNotPermittedException);

        CallNotPermittedException thrown = assertThrows(CallNotPermittedException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(callNotPermittedException, thrown);
    }

    @Test
    void shouldHandleNullBaseMessageGracefully() {
        HttpTimeoutException originalException = new HttpTimeoutException("Error: request timed out!");
        FederatorTokenException federatorTokenException = new FederatorTokenException(null, originalException,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals("" + " (timeout while calling " + COMPONENT_NAME + " for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }

    @Test
    void shouldHandleNullCauseGracefully() {
        FederatorTokenException federatorTokenException = new FederatorTokenException(BASE_MESSAGE, null,
                COMPONENT_NAME, OPERATION, TARGET_ID);
        Mockito.when(supplier.get()).thenThrow(federatorTokenException);

        FederatorTokenException thrown = assertThrows(FederatorTokenException.class,
                () -> ResilienceSupport.decorateAndExecute(COMPONENT_NAME, supplier));

        assertEquals(BASE_MESSAGE + " (unexpected failure during " + OPERATION + " for " + TARGET_ID + ")",
                thrown.getMessage());
        assertEquals(federatorTokenException, thrown.getCause());
    }
}
