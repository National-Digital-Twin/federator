// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.management;

import java.net.ConnectException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

/**
 * Handler for retrieving configurations from Management Node.
 * Manages HTTP communication and token authentication.
 */
@Slf4j
public class ManagementNodeDataHandler
        implements ManagementNodeDataHandlerInterface {

    static final String PRODUCER_PATH =
            "/api/v1/configuration/producer";
    static final String CONSUMER_PATH =
            "/api/v1/configuration/consumer";

    private static final int HTTP_OK = 200;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON_TYPE = "application/json";
    private static final String BASE_URL_PROP =
            "management.node.base.url";
    private static final String TIMEOUT_PROP =
            "management.node.request.timeout";
    private static final String SLASH = "/";
    private static final String ERR_NULL_CLIENT =
            "HttpClient must not be null";
    private static final String ERR_NULL_MAPPER =
            "ObjectMapper must not be null";
    private static final String ERR_NULL_SERVICE =
            "TokenService must not be null";
    private static final String ERR_EMPTY_URL =
            "Base URL cannot be empty";
    private static final String ERR_MISSING_PROP =
            "Missing required property: ";
    private static final String ERR_NULL_TOKEN =
            "Received null or empty token";
    private static final String ERR_INVALID_TOKEN =
            "Unable to obtain valid token";
    private static final String ERR_REQUEST_FAILED =
            "Request failed: %d";
    private static final String ERR_RESPONSE =
            "Failed to process response";
    private static final String ERR_INTERRUPTED =
            "Request interrupted";
    private static final String ERR_TOKEN_FETCH =
            "Failed to obtain token";
    private static final String LOG_INIT =
            "Handler init [url={}]";
    private static final String LOG_FETCH = "Fetch [url={}]";
    private static final String LOG_TOKEN_RETRY =
            "Token verify failed, retry";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final IdpTokenService tokenService;
    private final String baseUrl;
    private final Duration requestTimeout;

    /**
     * Constructs handler with required dependencies.
     *
     * @param client HTTP client for requests
     * @param mapper JSON mapper for responses
     * @param service service for JWT tokens
     * @throws NullPointerException if any parameter is null
     * @throws IllegalStateException if required properties missing
     */
    public ManagementNodeDataHandler(
            final HttpClient client,
            final ObjectMapper mapper,
            final IdpTokenService service) {
        this.httpClient = Objects.requireNonNull(
                client, ERR_NULL_CLIENT);
        this.objectMapper = Objects.requireNonNull(
                mapper, ERR_NULL_MAPPER);
        this.tokenService = Objects.requireNonNull(
                service, ERR_NULL_SERVICE);
        this.baseUrl = loadRequiredUrl();
        this.requestTimeout = loadTimeout();
        log.info(LOG_INIT, baseUrl);
    }

    /**
     * Retrieves producer configuration data.
     *
     * @param producerId the producer identifier
     * @return producer configuration DTO
     * @throws ManagementNodeDataException if retrieval fails
     */
    @Override
    public ProducerConfigDTO getProducerData(final String producerId)
            throws ManagementNodeDataException {
        final String endpoint = buildEndpoint(PRODUCER_PATH, producerId);
        log.debug("Get producer [id={}, path={}]", producerId, endpoint);
        return fetchConfiguration(endpoint, ProducerConfigDTO.class);
    }

    /**
     * Retrieves consumer configuration data.
     *
     * @param consumerId the consumer identifier
     * @return consumer configuration DTO
     * @throws ManagementNodeDataException if retrieval fails
     */
    @Override
    public ConsumerConfigDTO getConsumerData(final String consumerId)
            throws ManagementNodeDataException {
        final String endpoint = buildEndpoint(CONSUMER_PATH, consumerId);
        log.debug("Get consumer [id={}, path={}]", consumerId, endpoint);
        return fetchConfiguration(endpoint, ConsumerConfigDTO.class);
    }

    private String loadRequiredUrl() {
        final String url;
        try {
            url = PropertyUtil.getPropertyValue(BASE_URL_PROP);
        } catch (Exception e) {
            throw new IllegalStateException(
                    ERR_MISSING_PROP + BASE_URL_PROP, e);
        }
        return normalizeUrl(url);
    }

    private Duration loadTimeout() {
        try {
            final String timeoutStr =
                    PropertyUtil.getPropertyValue(TIMEOUT_PROP);
            return Duration.ofSeconds(Long.parseLong(timeoutStr));
        } catch (Exception e) {
            throw new IllegalStateException(
                    ERR_MISSING_PROP + TIMEOUT_PROP, e);
        }
    }

    private <T> T fetchConfiguration(
            final String endpoint,
            final Class<T> responseType)
            throws ManagementNodeDataException {
        final String token = fetchValidToken();
        final HttpRequest request = buildRequest(endpoint, token);
        return executeRequest(request, responseType);
    }

    private HttpRequest buildRequest(
            final String endpoint,
            final String token) {
        final String url = baseUrl + endpoint;
        log.debug(LOG_FETCH, url);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(AUTH_HEADER, BEARER + token)
                .header(CONTENT_TYPE, JSON_TYPE)
                .timeout(requestTimeout)
                .GET()
                .build();
    }

    private <T> T executeRequest(
            final HttpRequest request,
            final Class<T> responseType)
            throws ManagementNodeDataException {
        try {
            log.debug("Send HTTPS request [uri={}]", request.uri());
            final HttpResponse<String> response = httpClient.send(
                    request, BodyHandlers.ofString());
            log.debug("Response [status={}, body-length={}]",
                    response.statusCode(),
                    response.body() != null ? response.body().length() : 0);
            validateResponse(response);
            if (response.body() == null || response.body().isEmpty()) {
                log.error("Empty response body [uri={}]", request.uri());
                throw new ManagementNodeDataException("Empty response");
            }
            return objectMapper.readValue(
                    response.body(), responseType);
        } catch (ConnectException e) {
            log.error("Connection failed [uri={}, msg={}]",
                    request.uri(), e.getMessage());
            throw new ManagementNodeDataException(
                    "Cannot connect (check HTTPS/TLS): " + e.getMessage(), e);
        } catch (javax.net.ssl.SSLException e) {
            log.error("SSL/TLS error [uri={}, msg={}]",
                    request.uri(), e.getMessage());
            throw new ManagementNodeDataException(
                    "SSL/TLS failed (check certificates): " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("IO error [uri={}, type={}, msg={}]",
                    request.uri(), e.getClass().getSimpleName(),
                    e.getMessage());
            throw new ManagementNodeDataException(ERR_RESPONSE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Request interrupted [uri={}]", request.uri());
            throw new ManagementNodeDataException(ERR_INTERRUPTED, e);
        }
    }

    private void validateResponse(final HttpResponse<String> response)
            throws ManagementNodeDataException {
        if (response.statusCode() != HTTP_OK) {
            log.error("HTTP error [status={}, uri={}]",
                    response.statusCode(), response.uri());
            throw new ManagementNodeDataException(
                    String.format(ERR_REQUEST_FAILED,
                            response.statusCode()));
        }
    }

    private String fetchValidToken()
            throws ManagementNodeDataException {
        try {
            String token = tokenService.fetchToken();
            validateToken(token);
            if (!tokenService.verifyToken(token)) {
                token = retryTokenFetch();
            }
            return token;
        } catch (ManagementNodeDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token fetch error [err={}]", e.getMessage());
            throw new ManagementNodeDataException(ERR_TOKEN_FETCH, e);
        }
    }

    private void validateToken(final String token)
            throws ManagementNodeDataException {
        if (token == null || token.trim().isEmpty()) {
            log.error("Invalid token [null={}]", token == null);
            throw new ManagementNodeDataException(ERR_NULL_TOKEN);
        }
    }

    private String retryTokenFetch()
            throws ManagementNodeDataException {
        log.warn(LOG_TOKEN_RETRY);
        final String newToken = tokenService.fetchToken();
        if (newToken == null || !tokenService.verifyToken(newToken)) {
            log.error("Token retry failed");
            throw new ManagementNodeDataException(ERR_INVALID_TOKEN);
        }
        return newToken;
    }

    private String buildEndpoint(
            final String basePath,
            final String id) {
        if (id != null && !id.trim().isEmpty()) {
            return basePath + SLASH + id.trim();
        }
        return basePath;
    }

    private String normalizeUrl(final String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException(ERR_EMPTY_URL);
        }
        final String trimmed = url.trim();
        return trimmed.endsWith(SLASH)
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }
}