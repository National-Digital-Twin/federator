// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
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

/**
 * gRPC Client Interceptor to add Authorization header with Bearer token to outgoing requests.
 */
@AllArgsConstructor
public class AuthClientInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";
    private final IdpTokenService idpTokenService;

    /**
     * Intercepts outgoing gRPC calls to add Authorization header.
     * @param method
     * @param callOptions
     * @param next
     * @return
     * @param <I>
     * @param <O>
     */
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
