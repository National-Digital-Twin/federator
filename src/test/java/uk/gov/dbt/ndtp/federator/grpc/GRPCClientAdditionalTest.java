// SPDX-License-Identifier: Apache-2.0
// © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
// and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

package uk.gov.dbt.ndtp.federator.grpc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Additional coverage for GRPCClient focusing on non-TLS channel path and close behavior.
 */
class GRPCClientAdditionalTest {

    @Test
    void constructor_nonTls_uses_generateChannel_and_close_shuts_down_channel() throws Exception {
        ManagedChannel channel = mock(ManagedChannel.class);
        when(channel.shutdown()).thenReturn(channel);
        try (MockedStatic<GRPCClient> grpcStatic = mockStatic(GRPCClient.class)) {
            // Let unspecified static methods call real methods
            grpcStatic.when(() -> GRPCClient.generateChannel(anyString(), anyInt())).thenReturn(channel);
            // Build client with TLS disabled so it uses generateChannel
            GRPCClient client = new GRPCClient("client", "key", "server", "host", 1234, false, "pref");

            grpcStatic.verify(() -> GRPCClient.generateChannel("host", 1234));

            // When closing, ensure the underlying channel is shutdown
            client.close();
            verify(channel, times(1)).shutdown();
            verify(channel, times(1)).awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
