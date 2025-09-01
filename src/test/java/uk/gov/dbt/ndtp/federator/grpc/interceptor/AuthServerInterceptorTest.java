package uk.gov.dbt.ndtp.federator.grpc.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;

class AuthServerInterceptorTest {

    private IdpTokenService idpTokenService;
    private ServerCall<String, String> call;
    private ServerCallHandler<String, String> handler;
    private AuthServerInterceptor interceptor;
    private Metadata headers;

    @BeforeEach
    void setUp() {
        idpTokenService = mock(IdpTokenService.class);
        call = mock(ServerCall.class);
        handler = mock(ServerCallHandler.class);
        interceptor = new AuthServerInterceptor(idpTokenService);
        headers = new Metadata();

        MethodDescriptor<String, String> methodDescriptor = mock(MethodDescriptor.class);
        when(methodDescriptor.getFullMethodName()).thenReturn("test/method");
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(call.getAuthority()).thenReturn("testAuthority");
    }

    @Test
    void testMissingAuthorizationHeader() {
        ServerCall.Listener<String> listener = interceptor.interceptCall(call, headers, handler);
        assertNotNull(listener);

        verify(call)
                .close(
                        argThat(status -> status.getCode() == Status.UNAUTHENTICATED.getCode()
                                && status.getDescription() != null),
                        any(Metadata.class));
        verifyNoInteractions(handler);
    }

    @Test
    void testInvalidAuthorizationHeader() {
        headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "InvalidHeader");
        ServerCall.Listener<String> listener = interceptor.interceptCall(call, headers, handler);
        assertNotNull(listener);

        verify(call)
                .close(
                        argThat(status -> status.getCode() == Status.UNAUTHENTICATED.getCode()
                                && status.getDescription() != null),
                        any(Metadata.class));
        verifyNoInteractions(handler);
    }

    @Test
    void testInvalidToken() {
        headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer invalidtoken");
        when(idpTokenService.verifyToken("invalidtoken")).thenReturn(false);

        ServerCall.Listener<String> listener = interceptor.interceptCall(call, headers, handler);
        assertNotNull(listener);

        verify(call)
                .close(
                        argThat(status -> status.getCode() == Status.UNAUTHENTICATED.getCode()
                                && status.getDescription() != null),
                        any(Metadata.class));
        verifyNoInteractions(handler);
    }
}
