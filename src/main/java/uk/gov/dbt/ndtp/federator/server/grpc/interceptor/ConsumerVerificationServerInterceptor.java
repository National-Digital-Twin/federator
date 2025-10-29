// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.

/*
 gRPC ServerInterceptor to verify consumer identity from JWT tokens.
 Extracts consumer ID from the token, checks consumer authorization,
 and attaches the consumer ID to the gRPC context for downstream use.
*/
package uk.gov.dbt.ndtp.federator.server.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uk.gov.dbt.ndtp.federator.common.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.utils.ProducerConsumerConfigServiceFactory;
import uk.gov.dbt.ndtp.federator.server.grpc.GRPCContextKeys;

@Slf4j
@AllArgsConstructor
public class ConsumerVerificationServerInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String IDP_CLIENT_ID = "idp.client.id";
    private final IdpTokenService idpTokenService;
    private final Properties commonProperties;

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

        final String method = getMethodName(call);
        log.debug("Intercepting gRPC call method={}, authority={}", method, call.getAuthority());

        final String authHeader = getAuthHeader(headers); // may throw IllegalArgumentException (kept behaviour)
        final String token = extractBearerToken(authHeader);

        final String consumerId = extractAndValidateConsumerId(token, method, call);
        if (consumerId == null) {
            return new ServerCall.Listener<>() {};
        }

        if (!validateAudience(token, call)) {
            return new ServerCall.Listener<>() {};
        }

        final ProducerConfigDTO producerConfiguration = fetchProducerConfig();
        if (!authorizeOrClose(producerConfiguration, consumerId, method, call)) {
            return new ServerCall.Listener<>() {};
        }

        final Context contextWithClientId = Context.current().withValue(GRPCContextKeys.CLIENT_ID, consumerId);
        log.debug("Authentication succeeded method={}", method);
        return Contexts.interceptCall(contextWithClientId, call, headers, next);
    }

    private <T, R> String getMethodName(ServerCall<T, R> call) {
        return call.getMethodDescriptor() != null ? call.getMethodDescriptor().getFullMethodName() : "unknown";
    }

    private String getAuthHeader(Metadata headers) {
        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader == null) {
            throw new IllegalArgumentException("Missing Authorization header");
        }
        return authHeader;
    }

    private String extractBearerToken(String authHeader) {
        // Existing behaviour assumes a valid Bearer prefix and substrings after it
        return authHeader.substring(BEARER_PREFIX.length());
    }

    private <T, R> String extractAndValidateConsumerId(String token, String method, ServerCall<T, R> call) {
        String consumerId = idpTokenService.extractClientIdFromToken(token);
        log.debug("Extracted consumer ID from token: {}", consumerId);
        if (StringUtils.isBlank(consumerId)) {
            log.error("JWT token Authentication failed: missing or invalid client ID {} method={}", consumerId, method);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid client ID in token"), new Metadata());
            return null;
        }
        return consumerId;
    }

    private <T, R> boolean validateAudience(String token, ServerCall<T, R> call) {
        List<String> audiences = idpTokenService.extractAudiencesFromToken(token);
        String idpClientId = commonProperties.getProperty(IDP_CLIENT_ID);
        boolean audienceMatches = audiences != null
                && audiences.stream().filter(Objects::nonNull).anyMatch(aud -> aud.equalsIgnoreCase(idpClientId));
        if (!audienceMatches) {
            String message = "Token audience does not include required client id: " + idpClientId;
            log.error("JWT token Authentication failed: {} not present in token audiences={}", message, audiences);
            call.close(Status.UNAUTHENTICATED.withDescription(message), new Metadata());
            return false;
        }
        return true;
    }

    private ProducerConfigDTO fetchProducerConfig() {
        return ProducerConsumerConfigServiceFactory.getProducerConfigService().getProducerConfiguration();
    }

    private <T, R> boolean authorizeOrClose(
            ProducerConfigDTO config, String consumerId, String method, ServerCall<T, R> call) {
        if (!isConsumerAuthorized(config, consumerId)) {
            log.error("Authentication failed: consumer ID {} not authorized method={}", consumerId, method);
            call.close(Status.PERMISSION_DENIED.withDescription("Consumer ID not authorized"), new Metadata());
            return false;
        }
        return true;
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
