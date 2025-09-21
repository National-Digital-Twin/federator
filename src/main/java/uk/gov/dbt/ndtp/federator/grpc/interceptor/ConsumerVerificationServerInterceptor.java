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
import org.apache.commons.lang3.StringUtils;
import uk.gov.dbt.ndtp.federator.grpc.GRPCContextKeys;
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
        if (StringUtils.isBlank(consumerId)) {
            log.error("JWT token Authentication failed: missing or invalid client ID {} method={}", consumerId, method);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid client ID in token"), new Metadata());
            return new ServerCall.Listener<T>() {};
        }

        ProducerConfigDTO producerConfiguration =
                ProducerConsumerConfigServiceFactory.getProducerConsumerConfigService()
                        .getProducerConfiguration();

        if (!isConsumerAuthorized(producerConfiguration, consumerId)) {
            log.error("Authentication failed: consumer ID {} not authorized method={}", consumerId, method);
            call.close(Status.PERMISSION_DENIED.withDescription("Consumer ID not authorized"), new Metadata());
            return new ServerCall.Listener<T>() {};
        }
        Context contextWithClientId = Context.current().withValue(GRPCContextKeys.CLIENT_ID, consumerId);
        log.debug("Authentication succeeded method={}", method);

        return Contexts.interceptCall(contextWithClientId, call, headers, next);
    }

    /**
     * Determines whether the calling consumer (identified by consumerId) is authorised according to the
     * current producer configuration.
     *
     * Behaviour and rules:
     * - If the overall configuration or its producers list is null, the consumer is treated as NOT authorised (returns false).
     * - Only the first producer entry is considered (current business rule).
     * - If that first producer or its products list is null/empty, there are no authorised consumers (returns false).
     * - For all products under the first producer, the method flattens their consumer lists and checks whether any
     *   consumer has an idpClientId matching the supplied consumerId (case-insensitive).
     * - If at least one match is found, the consumer IS authorised (returns true).
     * - If no matches are found, the consumer is NOT authorised (returns false).
     *
     * Null-safety:
     * - Null collections and null elements within collections are safely skipped.
     * - Null idpClientId values are ignored.
     *
     * @param producerConfiguration the configuration describing producers, products, and their authorised consumers
     * @param consumerId the IDP client identifier extracted from the caller's token (case-insensitive match)
     * @return true if the consumer is authorised by the configuration; false otherwise
     */
    private boolean isConsumerAuthorized(ProducerConfigDTO producerConfiguration, String consumerId) {
        // Not authorized if no configuration or no producers defined
        if (producerConfiguration == null || producerConfiguration.getProducers() == null) {
            return false;
        }

        return producerConfiguration.getProducers().stream()
                .findFirst()
                .map(firstProducer -> {
                    if (firstProducer == null || firstProducer.getProducts() == null) {
                        return false; // no products -> no authorized consumers
                    }
                    // Look at each consumer for all products and check if any consumer idpClientId matches consumerId
                    return firstProducer.getProducts().stream()
                            .filter(p -> p != null && p.getConsumers() != null)
                            .flatMap(p -> p.getConsumers().stream())
                            .filter(c -> c != null && c.getIdpClientId() != null)
                            .anyMatch(c -> consumerId.equalsIgnoreCase(c.getIdpClientId()));
                    // Return whether any match was found (authorised if true)
                })
                .orElse(false);
    }
}
