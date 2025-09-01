package uk.gov.dbt.ndtp.federator.grpc.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;

class AuthClientInterceptorTest {

    private IdpTokenService idpTokenService;
    private Channel channel;
    private ClientCall<String, String> clientCall;
    private AuthClientInterceptor interceptor;
    private MethodDescriptor<String, String> methodDescriptor;

    @BeforeEach
    void setUp() {
        idpTokenService = mock(IdpTokenService.class);
        channel = mock(Channel.class);
        // Use raw types to avoid generic type mismatch
        clientCall = mock(ClientCall.class);
        interceptor = new AuthClientInterceptor(idpTokenService);
        methodDescriptor = mock(MethodDescriptor.class);

        // Use raw types in stubbing
        when(channel.newCall(any(MethodDescriptor.class), any())).thenReturn(clientCall);
    }

    @Test
    void testAuthorizationHeaderIsAdded() {
        when(idpTokenService.fetchToken()).thenReturn("test-token");

        ClientCall<String, String> interceptedCall =
                interceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, channel);

        Metadata headers = new Metadata();
        ClientCall.Listener<String> listener = mock(ClientCall.Listener.class);

        interceptedCall.start(listener, headers);

        Metadata.Key<String> key = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        assertEquals("Bearer test-token", headers.get(key));
        verify(clientCall).start(listener, headers);
    }
}
