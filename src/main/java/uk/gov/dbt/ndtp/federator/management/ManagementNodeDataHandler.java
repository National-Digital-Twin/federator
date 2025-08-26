// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.
package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.JwtToken;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of ManagementNodeDataHandlerInterface for retrieving consumer
 * and producer configurations from the Management Node.
 * Provides secure access to configuration data using JWT authentication.
 *
 * <p>All configuration values including constants are externalized through PropertyUtil.
 * No values are hardcoded in this class.
 *
 * @see ManagementNodeDataHandlerInterface
 * @see JwtTokenService
 * @see PropertyUtil
 */
@Slf4j
public class ManagementNodeDataHandler implements ManagementNodeDataHandlerInterface {

    private static final int HTTP_OK = 200;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final JwtTokenService tokenService;
    private final String managementNodeBaseUrl;
    private final Duration requestTimeout;
    private final Duration connectivityTimeout;
    private final int serverErrorThreshold;
    private final String producerEndpoint;
    private final String consumerEndpoint;
    private final String authorizationHeader;
    private final String bearerPrefix;

    /**
     * Constructs a ManagementNodeDataHandler with required dependencies.
     * All configuration values are loaded from PropertyUtil.
     *
     * @param httpClient HTTP client for making requests
     * @param objectMapper JSON object mapper for parsing responses
     * @param tokenService JWT token validation service
     * @throws IllegalStateException if required properties are not found
     */
    public ManagementNodeDataHandler(final HttpClient httpClient,
                                     final ObjectMapper objectMapper,
                                     final JwtTokenService tokenService) {
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.tokenService = Objects.requireNonNull(tokenService, "JwtTokenService cannot be null");

        // Load all configuration from PropertyUtil - no defaults
        this.managementNodeBaseUrl = validateBaseUrl(
                PropertyUtil.getPropertyValue("management.node.base.url"));
        this.requestTimeout = Duration.ofSeconds(
                PropertyUtil.getPropertyIntValue("management.node.request.timeout"));
        this.connectivityTimeout = Duration.ofSeconds(
                PropertyUtil.getPropertyIntValue("management.node.connectivity.timeout"));
        this.serverErrorThreshold = PropertyUtil.getPropertyIntValue(
                "management.node.server.error.threshold");
        this.producerEndpoint = PropertyUtil.getPropertyValue(
                "management.node.api.endpoints.producer");
        this.consumerEndpoint = PropertyUtil.getPropertyValue(
                "management.node.api.endpoints.consumer");
        this.authorizationHeader = PropertyUtil.getPropertyValue(
                "management.node.http.headers.authorization");
        this.bearerPrefix = PropertyUtil.getPropertyValue(
                "management.node.http.headers.bearer.prefix");

        log.info("ManagementNodeDataHandler initialized with base URL: {}", managementNodeBaseUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProducerConfigDTO getProducerData(final Optional<String> producerId) throws IOException {
        final String endpoint = buildEndpoint(producerEndpoint, producerId);
        return fetchConfiguration(endpoint, ProducerConfigDTO.class, "producer");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerConfigDTO getConsumerData(final Optional<String> consumerId) throws IOException {
        final String endpoint = buildEndpoint(consumerEndpoint, consumerId);
        return fetchConfiguration(endpoint, ConsumerConfigDTO.class, "consumer");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkConnectivity() {
        try {
            // This can throw InterruptedException
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(managementNodeBaseUrl))
                    .timeout(connectivityTimeout)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            final HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());
            final boolean reachable = response.statusCode() < serverErrorThreshold;

            log.info("Management node {} at: {}",
                    reachable ? "reachable" : "unreachable", managementNodeBaseUrl);
            return reachable;

        } catch (final InterruptedException e) {
            // At this point, the interrupt flag has been CLEARED
            Thread.currentThread().interrupt(); // RE-SET the interrupt flag
            log.error("Connectivity test interrupted: {}", e.getMessage());
            return false;
        } catch (final IOException e) {
            log.error("Failed to connect to management node: {}", e.getMessage());
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getManagementNodeBaseUrl() {
        return managementNodeBaseUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigured() {
        return httpClient != null
                && objectMapper != null
                && managementNodeBaseUrl != null && !managementNodeBaseUrl.isEmpty()
                && tokenService != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRequestTimeoutSeconds() {
        return requestTimeout.getSeconds();
    }

    /**
     * Builds the endpoint URL with optional ID parameter.
     *
     * @param baseEndpoint the base endpoint path
     * @param optionalId optional ID to append to the endpoint
     * @return the complete endpoint path
     */
    private String buildEndpoint(final String baseEndpoint, final Optional<String> optionalId) {
        return optionalId
                .filter(id -> !id.trim().isEmpty())
                .map(id -> baseEndpoint + "/" + id)
                .orElse(baseEndpoint);
    }

    /**
     * Fetches configuration from the specified endpoint.
     *
     * @param endpoint the API endpoint to call
     * @param responseType the class type of the response
     * @param configType description of configuration type for logging
     * @param <T> the type of configuration DTO
     * @return the configuration DTO
     * @throws IOException if request fails or response cannot be parsed
     */
    private <T> T fetchConfiguration(final String endpoint,
                                     final Class<T> responseType,
                                     final String configType) throws IOException {
        final String jwtToken = fetchAndValidateToken();
        final String url = managementNodeBaseUrl + endpoint;

        log.debug("Fetching {} data from: {}", configType, url);

        final HttpResponse<String> response = executeRequest(url, jwtToken);
        if (response.statusCode() != HTTP_OK) {
            throw new IOException(String.format("Failed to retrieve %s data. HTTP %d",
                    configType, response.statusCode()));
        }

        final T configDto = objectMapper.readValue(response.body(), responseType);
        log.info("Successfully retrieved {} configuration", configType);
        return configDto;
    }

    /**
     * Fetches and validates JWT token.
     *
     * @return valid JWT token string
     * @throws IOException if token fetch or validation fails
     */
    private String fetchAndValidateToken() throws IOException {
        try {
            final JwtToken jwtToken = tokenService.fetchJwtToken();
            if (jwtToken.isExpired()) {
                throw new IOException("JWT token is expired");
            }

            log.debug("Token valid for {} more seconds", jwtToken.getRemainingValidity());
            return jwtToken.getToken();
        } catch (final IllegalStateException e) {
            throw new IOException("Failed to fetch JWT token", e);
        }
    }

    /**
     * Executes HTTP GET request to the management node.
     *
     * @param url target URL
     * @param jwtToken JWT token for authentication
     * @return HTTP response with string body
     * @throws IOException if request fails
     */
    private HttpResponse<String> executeRequest(final String url,
                                                final String jwtToken) throws IOException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(authorizationHeader, bearerPrefix + jwtToken)
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .GET()
                .build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final IOException e) {
            log.error("Request failed: {}", e.getMessage());
            throw e;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted", e);
        }
    }

    /**
     * Validates and normalizes the base URL format.
     *
     * @param baseUrl the base URL to validate
     * @return the validated base URL without trailing slash
     */
    private static String validateBaseUrl(final String baseUrl) {
        Objects.requireNonNull(baseUrl, "Base URL cannot be null");
        final String trimmedUrl = baseUrl.trim();
        if (trimmedUrl.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be empty");
        }
        return trimmedUrl.endsWith("/")
                ? trimmedUrl.substring(0, trimmedUrl.length() - 1)
                : trimmedUrl;
    }
}