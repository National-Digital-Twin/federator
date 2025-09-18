// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.

/**
 * gRPC ServerInterceptor to verify consumer identity from JWT tokens.
 * Extracts consumer ID from the token, checks consumer authorization,
 * and attaches the consumer ID to the gRPC context for downstream use.
 */
package uk.gov.dbt.ndtp.federator.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.grpc.GRPCContextKeys;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.utils.ProducerConsumerConfigServiceFactory;

@Slf4j
@AllArgsConstructor
public class ConsumerVerificationServerInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";
    private final IdpTokenService idpTokenService;

    /**
     * Intercepts incoming gRPC calls to verify consumer identity.
     *
     * @param call    The server call object.
     * @param headers The metadata headers from the call.
     * @param next    The next server call handler in the chain.
     * @param <T>     The type of request message.
     * @param <R>     The type of response message.
     * @return A listener for processing incoming messages.
     */
    @Override
    public <T, R> ServerCall.Listener<T> interceptCall(
            ServerCall<T, R> call, Metadata headers, ServerCallHandler<T, R> next) {

        String method =
                call.getMethodDescriptor() != null ? call.getMethodDescriptor().getFullMethodName() : "unknown";
        log.debug("Intercepting gRPC call method={}, authority={}", method, call.getAuthority());

        String authHeader = headers.get(AUTHORIZATION_KEY);
        String token = authHeader.substring(BEARER_PREFIX.length());

        String consumerId = idpTokenService.extractClientIdFromToken(token);
        if (consumerId == null || consumerId.isEmpty()) {
            log.error("JWT token Authentication failed: missing or invalid client ID method={}", method);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid client ID in token"), new Metadata());
            return new ServerCall.Listener<T>() {};
        }

        ConsumerConfigDTO consumerConfiguration =
                ProducerConsumerConfigServiceFactory.getProducerConsumerConfigService()
                        .getConsumerConfiguration();

        log.debug("--------------PRODUCER----------------------------------------");
        consumerConfiguration.getProducers().forEach(producers -> {
            producers.getDataProviders().forEach(x -> {
                log.debug(" PRODUCERS: --> name : {}  ,topic: {},  ", x.getName(), x.getTopic());
                x.getConsumers().forEach(consumers -> {
                    log.debug(
                            " CONSUMERS: --> name : {}  ,topic: {},  ",
                            consumers.getName(),
                            consumers.getIdpClientId());
                });
            });
        });

        ProducerConfigDTO producerConfiguration =
                ProducerConsumerConfigServiceFactory.getProducerConsumerConfigService()
                        .getProducerConfiguration();

        log.debug("--------------CONSUMERS----------------------------------------");
        producerConfiguration.getProducers().forEach(producers -> {
            producers.getDataProviders().forEach(x -> {
                log.debug(" PRODUCERS: --> name : {}  ,topic: {},  ", x.getName(), x.getTopic());
                x.getConsumers().forEach(consumers -> {
                    log.debug(
                            " CONSUMERS: --> name : {}  ,topic: {},  ",
                            consumers.getName(),
                            consumers.getIdpClientId());
                });
            });
        });

        if (consumerConfiguration == null
                || consumerConfiguration.getProducers().stream()
                        .noneMatch(p -> consumerId.equalsIgnoreCase(p.getIdpClientId()))) {
            log.error("Authentication failed: consumer ID {} not authorized method={}", consumerId, method);
            call.close(Status.PERMISSION_DENIED.withDescription("Consumer ID not authorized"), new Metadata());
            return new ServerCall.Listener<T>() {};
        }
        Context contextWithClientId = Context.current().withValue(GRPCContextKeys.CLIENT_ID, consumerId);
        log.debug("Authentication succeeded method={}", method);

        return Contexts.interceptCall(contextWithClientId, call, headers, next);
    }
}
