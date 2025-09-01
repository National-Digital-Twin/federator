package uk.gov.dbt.ndtp.federator.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.AllArgsConstructor;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;

@AllArgsConstructor
public class AuthClientInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";
    private final IdpTokenService idpTokenService;

    @Override
    public <I, O> ClientCall<I, O> interceptCall(MethodDescriptor<I, O> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<O> responseListener, Metadata headers) {
                headers.put(AUTHORIZATION_KEY, BEARER_PREFIX + idpTokenService.fetchToken());
                super.start(responseListener, headers);
            }
        };
    }
}
