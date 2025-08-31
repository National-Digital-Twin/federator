// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.IdpTokenService;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

/**
 * Handler for retrieving configurations from Management Node.
 */
@Slf4j
public class ManagementNodeDataHandler implements ManagementNodeDataHandlerInterface {

    /**
     * HTTP success status code.
     */
    private static final int HTTP_OK = 200;

    /**
     * Authorization header name.
     */
    private static final String AUTH_HEADER = "Authorization";

    /**
     * Bearer token prefix.
     */
    private static final String BEARER = "Bearer ";

    /**
     * Content type header name.
     */
    private static final String CONTENT_TYPE = "Content-Type";

    /**
     * JSON content type value.
     */
    private static final String JSON_TYPE = "application/json";

    /**
     * Common configuration property key.
     */
    private static final String COMMON_CONFIG_KEY = "common.configuration";

    /**
     * Base URL property key.
     */
    private static final String BASE_URL_PROP = "management.node.base.url";

    /**
     * Request timeout property key.
     */
    private static final String TIMEOUT_PROP = "management.node.request.timeout";

    /**
     * Producer endpoint path property key.
     */
    private static final String PRODUCER_PATH_PROP = "management.node.api.endpoints.producer";

    /**
     * Consumer endpoint path property key.
     */
    private static final String CONSUMER_PATH_PROP = "management.node.api.endpoints.consumer";

    /**
     * HTTP client for making requests.
     */
    private final HttpClient httpClient;

    /**
     * JSON object mapper.
     */
    private final ObjectMapper objectMapper;

    /**
     * Token service for authentication.
     */
    private final IdpTokenService tokenService;

    /**
     * Base URL for management node.
     */
    private final String baseUrl;

    /**
     * Producer endpoint path.
     */
    private final String producerPath;

    /**
     * Consumer endpoint path.
     */
    private final String consumerPath;

    /**
     * Request timeout duration.
     */
    private final Duration requestTimeout;

    /**
     * Constructs handler with required dependencies.
     *
     * @param client HTTP client for requests
     * @param mapper JSON mapper for responses
     * @param service service for JWT tokens
     * @throws NullPointerException if any parameter is null
     * @throws IllegalStateException if required properties are missing
     */
    public ManagementNodeDataHandler(
            final HttpClient client, final ObjectMapper mapper, final IdpTokenService service) {
        this.httpClient = Objects.requireNonNull(client, "HttpClient must not be null");
        this.objectMapper = Objects.requireNonNull(mapper, "ObjectMapper must not be null");
        this.tokenService = Objects.requireNonNull(service, "TokenService must not be null");

        // Load properties from common configuration file
        Properties commonProps = PropertyUtil.getPropertiesFromAbsoluteFilePath(COMMON_CONFIG_KEY);

        // Get required properties - fail if any are missing
        String baseUrlValue = commonProps.getProperty(BASE_URL_PROP);
        if (baseUrlValue == null) {
            throw new IllegalStateException("Missing required property: " + BASE_URL_PROP);
        }
        this.baseUrl = normalizeUrl(baseUrlValue);

        this.producerPath = commonProps.getProperty(PRODUCER_PATH_PROP);
        if (this.producerPath == null) {
            throw new IllegalStateException("Missing required property: " + PRODUCER_PATH_PROP);
        }

        this.consumerPath = commonProps.getProperty(CONSUMER_PATH_PROP);
        if (this.consumerPath == null) {
            throw new IllegalStateException("Missing required property: " + CONSUMER_PATH_PROP);
        }

        String timeoutStr = commonProps.getProperty(TIMEOUT_PROP);
        if (timeoutStr == null) {
            throw new IllegalStateException("Missing required property: " + TIMEOUT_PROP);
        }
        this.requestTimeout = Duration.ofSeconds(Long.parseLong(timeoutStr));

        log.info("Handler initialized - URL: {}, Producer: {}, Consumer: {}", baseUrl, producerPath, consumerPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProducerConfigDTO getProducerData(final String producerId) throws ManagementNodeDataException {
        final String endpoint = buildEndpoint(producerPath, producerId);
        return fetchConfiguration(endpoint, ProducerConfigDTO.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerConfigDTO getConsumerData(final String consumerId) throws ManagementNodeDataException {
        final String endpoint = buildEndpoint(consumerPath, consumerId);
        return fetchConfiguration(endpoint, ConsumerConfigDTO.class);
    }

    /**
     * Fetches configuration from endpoint.
     *
     * @param <T> type of configuration
     * @param endpoint API endpoint path
     * @param responseType expected response class
     * @return configuration object
     * @throws ManagementNodeDataException on fetch failure
     */
    private <T> T fetchConfiguration(final String endpoint, final Class<T> responseType)
            throws ManagementNodeDataException {
        final String token = fetchValidToken();
        final String url = baseUrl + endpoint;

        log.debug("Fetching from: {}", url);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(AUTH_HEADER, BEARER + token)
                .header(CONTENT_TYPE, JSON_TYPE)
                .timeout(requestTimeout)
                .GET()
                .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HTTP_OK) {
                throw new ManagementNodeDataException(String.format("Request failed: %d", response.statusCode()));
            }

            return objectMapper.readValue(response.body(), responseType);

        } catch (IOException e) {
            throw new ManagementNodeDataException("Failed to process response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ManagementNodeDataException("Request interrupted", e);
        }
    }

    /**
     * Fetches and validates token.
     *
     * @return valid authentication token
     * @throws ManagementNodeDataException on token fetch failure
     */
    private String fetchValidToken() throws ManagementNodeDataException {
        try {
            final String token = tokenService.fetchToken();

            if (token == null || token.trim().isEmpty()) {
                throw new ManagementNodeDataException("Received null or empty token");
            }

            if (!tokenService.verifyToken(token)) {
                log.warn("Token verification failed, retrying");
                final String newToken = tokenService.fetchToken();
                if (newToken == null || !tokenService.verifyToken(newToken)) {
                    throw new ManagementNodeDataException("Unable to obtain valid token");
                }
                return newToken;
            }

            return token;

        } catch (Exception e) {
            if (e instanceof ManagementNodeDataException mnde) {
                throw mnde;
            }
            throw new ManagementNodeDataException("Failed to obtain token", e);
        }
    }

    /**
     * Builds endpoint with optional ID.
     *
     * @param basePath base API path
     * @param id optional identifier
     * @return complete endpoint path
     */
    private String buildEndpoint(final String basePath, final String id) {
        if (id != null && !id.trim().isEmpty()) {
            return basePath + "/" + id.trim();
        }
        return basePath;
    }

    /**
     * Normalizes URL by removing trailing slash.
     *
     * @param url URL to normalize
     * @return normalized URL
     */
    private String normalizeUrl(final String url) {
        Objects.requireNonNull(url, "Base URL must not be null");
        final String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be empty");
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
