package uk.gov.dbt.ndtp.federator.grpc.interceptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;

@Slf4j
@AllArgsConstructor
public class AuthServerInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";
    private final IdpTokenService idpTokenService;

    @Override
    public <T, R> ServerCall.Listener<T> interceptCall(
            ServerCall<T, R> call, Metadata headers, ServerCallHandler<T, R> next) {

        String method =
                call.getMethodDescriptor() != null ? call.getMethodDescriptor().getFullMethodName() : "unknown";
        log.debug("Intercepting gRPC call method={}, authority={}", method, call.getAuthority());

        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.error("JWT token Authentication failed: missing or invalid header method={}", method);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Missing or invalid Authorization header"), new Metadata());
            return new ServerCall.Listener<T>() {};
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!idpTokenService.verifyToken(token)) {
            log.error("Authentication failed: invalid token method={}", method);
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), new Metadata());
            return new ServerCall.Listener<T>() {};
        }

        log.debug("Authentication succeeded method={}", method);
        return next.startCall(call, headers);
    }
}
