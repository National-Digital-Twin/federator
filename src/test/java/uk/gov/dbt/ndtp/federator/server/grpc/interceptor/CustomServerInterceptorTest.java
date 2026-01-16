/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.server.grpc.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.*;
import org.junit.jupiter.api.Test;

class CustomServerInterceptorTest {

    @Test
    @SuppressWarnings("unchecked")
    void testInterceptCall() {
        CustomServerInterceptor interceptor = new CustomServerInterceptor();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata headers = new Metadata();
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ServerCall.Listener<String> mockListener = mock(ServerCall.Listener.class);
        when(next.startCall(any(), any())).thenReturn(mockListener);

        ServerCall.Listener<String> listener = interceptor.interceptCall(call, headers, next);

        verify(next).startCall(any(ForwardingServerCall.class), eq(headers));
        assert listener == mockListener;
    }
}
