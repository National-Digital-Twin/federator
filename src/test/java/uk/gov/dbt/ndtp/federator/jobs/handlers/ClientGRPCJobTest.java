package uk.gov.dbt.ndtp.federator.jobs.handlers;

import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.WrappedGRPCClient;
import uk.gov.dbt.ndtp.federator.client.connection.ConnectionProperties;
import uk.gov.dbt.ndtp.federator.jobs.params.ClientGRPCJobParams;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.ToLongBiFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class ClientGRPCJobTest {

    @Test
    void run_invokes_wrapped_client_with_prefix_and_offset_from_injected_dependencies() {
        // Arrange
        Supplier<String> prefixSupplier = () -> "pref";
        ToLongBiFunction<String, String> offsetProvider = mock(ToLongBiFunction.class);
        when(offsetProvider.applyAsLong("pref", "topic-a")).thenReturn(42L);

        WrappedGRPCClient wrapped = mock(WrappedGRPCClient.class);
        BiFunction<ConnectionProperties, String, WrappedGRPCClient> clientFactory = mock(BiFunction.class);

        ConnectionProperties cp = new ConnectionProperties("cName","cKey","sName","localhost",8080,false);

        when(clientFactory.apply(cp, "pref")).thenReturn(wrapped);

        ClientGRPCJob job = new ClientGRPCJob();
        job.setPrefixSupplier(prefixSupplier);
        job.setOffsetProvider(offsetProvider);
        job.setClientFactory(clientFactory);
        ClientGRPCJobParams params = new ClientGRPCJobParams("topic-a", cp, "node1");

        // Act
        assertDoesNotThrow(() -> job.run(params));

        // Assert
        verify(clientFactory, times(1)).apply(cp, "pref");
        verify(offsetProvider, times(1)).applyAsLong("pref", "topic-a");
        verify(wrapped, times(1)).processTopic("topic-a", 42L);
        verifyNoMoreInteractions(wrapped);
    }

    @Test
    void run_works_with_empty_prefix() {
        // Arrange
        Supplier<String> prefixSupplier = () -> ""; // explicit empty to ensure default works
        ToLongBiFunction<String, String> offsetProvider = mock(ToLongBiFunction.class);
        when(offsetProvider.applyAsLong("", "t")).thenReturn(0L);

        WrappedGRPCClient wrapped = mock(WrappedGRPCClient.class);
        BiFunction<ConnectionProperties, String, WrappedGRPCClient> clientFactory = mock(BiFunction.class);

        ConnectionProperties cp = new ConnectionProperties("c","k","S","127.0.0.1",8081,true);
        when(clientFactory.apply(cp, "")).thenReturn(wrapped);

        ClientGRPCJob job = new ClientGRPCJob();
        job.setPrefixSupplier(prefixSupplier);
        job.setOffsetProvider(offsetProvider);
        job.setClientFactory(clientFactory);
        ClientGRPCJobParams params = new ClientGRPCJobParams("t", cp, "nodeX");

        // Act
        assertDoesNotThrow(() -> job.run(params));

        // Assert
        verify(clientFactory).apply(cp, "");
        verify(offsetProvider).applyAsLong("", "t");
        verify(wrapped).processTopic("t", 0L);
    }
}
