// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.

/*
 gRPC ServerInterceptor to verify consumer identity from JWT tokens.
 Extracts consumer ID from the token, checks consumer authorization,
 and attaches the consumer ID to the gRPC context for downstream use.
*/
package uk.gov.dbt.ndtp.federator.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Objects;
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

        if (authHeader == null) throw new IllegalArgumentException("Missing Authorization header");

        String token = authHeader.substring(BEARER_PREFIX.length());

        String consumerId = idpTokenService.extractClientIdFromToken(token);
        log.debug("Extracted consumer ID from token: {}", consumerId);

        if (StringUtils.isBlank(consumerId)) {
            log.error("JWT token Authentication failed: missing or invalid client ID {} method={}", consumerId, method);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid client ID in token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        ProducerConfigDTO producerConfiguration =
                ProducerConsumerConfigServiceFactory.getProducerConsumerConfigService()
                        .getProducerConfiguration();

        if (!isConsumerAuthorized(producerConfiguration, consumerId)) {
            log.error("Authentication failed: consumer ID {} not authorized method={}", consumerId, method);
            call.close(Status.PERMISSION_DENIED.withDescription("Consumer ID not authorized"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        Context contextWithClientId = Context.current().withValue(GRPCContextKeys.CLIENT_ID, consumerId);
        log.debug("Authentication succeeded method={}", method);

        return Contexts.interceptCall(contextWithClientId, call, headers, next);
    }

    /**
     * Determines whether the calling consumer (identified by consumerId) is authorised according to the
     * current producer configuration.
     * Behaviour and rules:
     * - If the overall configuration or its producers list is null, the consumer is treated as NOT authorised (returns false).
     * - Only the first producer entry is considered (current business rule).
     * - If that first producer or its products list is null/empty, there are no authorised consumers (returns false).
     * - For all products under the first producer, the method flattens their consumer lists and checks whether any
     *   consumer has an idpClientId matching the supplied consumerId (case-insensitive).
     * - If at least one match is found, the consumer IS authorised (returns true).
     * - If no matches are found, the consumer is NOT authorised (returns false).
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
            log.warn("No producer configuration or producers defined");
            return false;
        }

        // Authorised if ANY consumer (across ALL producers/products) has a matching idpClientId
        return producerConfiguration.getProducers().stream()
                .filter(Objects::nonNull)
                .peek(producer -> {
                    if (producer.getProducts() == null) {
                        log.warn("No products defined for producer clientId: {}", producer.getIdpClientId());
                    }
                })
                .filter(producer -> producer.getProducts() != null)
                .flatMap(producer -> producer.getProducts().stream())
                .filter(product -> product != null && product.getConsumers() != null)
                .flatMap(product -> product.getConsumers().stream())
                .filter(consumer -> consumer != null && consumer.getIdpClientId() != null)
                .anyMatch(consumer -> consumerId.equalsIgnoreCase(consumer.getIdpClientId()));
    }
}
