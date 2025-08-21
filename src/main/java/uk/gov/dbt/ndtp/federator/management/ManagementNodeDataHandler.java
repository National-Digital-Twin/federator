package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Handler for retrieving consumer and producer configurations from the Management Node.
 * This class provides secure access to configuration data using JWT authentication.
 *
 * <p>Features:
 * <ul>
 *   <li>Retrieves producer and consumer configurations</li>
 *   <li>Validates JWT tokens before making requests</li>
 *   <li>Handles HTTP errors with detailed logging</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * @author Rakesh Chiluka
 * @version 1.0
 * @since 2025-01-20
 */
@Slf4j
public class ManagementNodeDataHandler {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private static final String PRODUCER_ENDPOINT = "/api/v1/configuration/producer";
    private static final String CONSUMER_ENDPOINT = "/api/v1/configuration/consumer";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SERVER_ERROR = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String managementNodeBaseUrl;
    private final JwtTokenService tokenService;
    private final Duration requestTimeout;

    /**
     * Constructs a ManagementNodeDataHandler with all required dependencies.
     *
     * @param httpClient HTTP client for making requests
     * @param objectMapper JSON object mapper for parsing responses
     * @param managementNodeBaseUrl base URL of the management node
     * @param tokenService JWT token validation service
     * @param requestTimeout timeout for HTTP requests
     * @throws NullPointerException if any required parameter is null
     * @throws IllegalArgumentException if base URL is empty
     */
    public ManagementNodeDataHandler(final HttpClient httpClient,
                                     final ObjectMapper objectMapper,
                                     final String managementNodeBaseUrl,
                                     final JwtTokenService tokenService,
                                     final Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient,
                "HttpClient cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper,
                "ObjectMapper cannot be null");
        this.managementNodeBaseUrl = validateBaseUrl(managementNodeBaseUrl);
        this.tokenService = Objects.requireNonNull(tokenService,
                "JwtTokenService cannot be null");
        this.requestTimeout = Objects.requireNonNullElse(requestTimeout, DEFAULT_TIMEOUT);

        log.info("ManagementNodeDataHandler initialized with base URL: {}",
                managementNodeBaseUrl);
    }

    /**
     * Constructs a ManagementNodeDataHandler with default timeout.
     *
     * @param httpClient HTTP client for making requests
     * @param objectMapper JSON object mapper for parsing responses
     * @param managementNodeBaseUrl base URL of the management node
     * @param tokenService JWT token validation service
     */
    public ManagementNodeDataHandler(final HttpClient httpClient,
                                     final ObjectMapper objectMapper,
                                     final String managementNodeBaseUrl,
                                     final JwtTokenService tokenService) {
        this(httpClient, objectMapper, managementNodeBaseUrl, tokenService, DEFAULT_TIMEOUT);
    }

    /**
     * Retrieves producer configuration data from the management node.
     *
     * @param jwtToken the JWT token for authentication (without Bearer prefix)
     * @return ProducerConfigDTO object containing producer configuration
     * @throws IllegalArgumentException if the JWT token is invalid
     * @throws IOException if the network request fails
     * @throws InterruptedException if the request is interrupted
     */
    public ProducerConfigDTO getProducerData(final String jwtToken, final String producerId)
            throws IOException, InterruptedException {
        validateToken(jwtToken);

        final String url = managementNodeBaseUrl + PRODUCER_ENDPOINT;
        log.debug("Fetching producer data from: {}", url);

        final HttpRequest request = buildRequest(url, jwtToken);
        final HttpResponse<String> response = executeRequest(request);

        validateResponse(response, "producer");

        final ProducerConfigDTO config = parseResponse(response.body(),
                ProducerConfigDTO.class);

        log.info("Successfully retrieved producer configuration for client: {}",
                config.getClientId());

        return config;
    }

    /**
     * Retrieves consumer configuration data from the management node.
     *
     * @param jwtToken the JWT token for authentication (without Bearer prefix)
     * @return ConsumerConfigDTO object containing consumer configuration
     * @throws IllegalArgumentException if the JWT token is invalid
     * @throws IOException if the network request fails
     * @throws InterruptedException if the request is interrupted
     */
    public ConsumerConfigDTO getConsumerData(final String jwtToken, final String consumerId)
            throws IOException, InterruptedException {
        validateToken(jwtToken);

        final String url = managementNodeBaseUrl + CONSUMER_ENDPOINT;
        log.debug("Fetching consumer data from: {}", url);

        final HttpRequest request = buildRequest(url, jwtToken);
        final HttpResponse<String> response = executeRequest(request);

        validateResponse(response, "consumer");

        final ConsumerConfigDTO config = parseResponse(response.body(),
                ConsumerConfigDTO.class);

        log.info("Successfully retrieved consumer configuration for client: {}",
                config.getClientId());

        return config;
    }

    /**
     * Tests connectivity to the management node.
     *
     * @return true if the management node is reachable, false otherwise
     */
    public boolean testConnectivity() {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(managementNodeBaseUrl))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            final HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());

            final boolean reachable = response.statusCode() < HTTP_SERVER_ERROR;

            if (reachable) {
                log.info("Management node is reachable at: {}", managementNodeBaseUrl);
            } else {
                log.warn("Management node returned status: {}", response.statusCode());
            }

            return reachable;
        } catch (final Exception e) {
            log.error("Failed to connect to management node: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates the base URL format.
     *
     * @param baseUrl the base URL to validate
     * @return the validated base URL (without trailing slash)
     * @throws NullPointerException if baseUrl is null
     * @throws IllegalArgumentException if baseUrl is empty or invalid
     */
    private String validateBaseUrl(final String baseUrl) {
        Objects.requireNonNull(baseUrl, "Base URL cannot be null");

        final String trimmedUrl = baseUrl.trim();
        if (trimmedUrl.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be empty");
        }

        // Remove trailing slash if present
        return trimmedUrl.endsWith("/")
                ? trimmedUrl.substring(0, trimmedUrl.length() - 1)
                : trimmedUrl;
    }

    /**
     * Validates the JWT token format and expiry.
     *
     * @param jwtToken the token to validate
     * @throws IllegalArgumentException if token is null, empty, or invalid
     */
    private void validateToken(final String jwtToken) {
        if (jwtToken == null || jwtToken.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT token cannot be null or empty");
        }

        if (!tokenService.isTokenValid(jwtToken)) {
            final String clientId = tokenService.extractClientId(jwtToken);
            log.error("Invalid or expired JWT token for client: {}", clientId);
            throw new IllegalArgumentException("JWT token is invalid or expired");
        }

        final long remaining = tokenService.getRemainingValidity(jwtToken);
        if (remaining > 0) {
            log.debug("Token valid for {} more seconds", remaining);
        }
    }

    /**
     * Builds an HTTP request with authentication headers.
     *
     * @param url the target URL
     * @param jwtToken the JWT token for authentication
     * @return configured HTTP request
     */
    private HttpRequest buildRequest(final String url, final String jwtToken) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + jwtToken)
                .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                .timeout(requestTimeout)
                .GET()
                .build();
    }

    /**
     * Executes an HTTP request and returns the response.
     *
     * @param request the HTTP request to execute
     * @return HTTP response with string body
     * @throws IOException if the request fails
     * @throws InterruptedException if the request is interrupted
     */
    private HttpResponse<String> executeRequest(final HttpRequest request)
            throws IOException, InterruptedException {
        log.debug("Executing request to: {}", request.uri());

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final IOException | InterruptedException e) {
            log.error("Request failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Validates the HTTP response status and content.
     *
     * @param response the HTTP response to validate
     * @param dataType the type of data being retrieved (for logging)
     * @throws IOException if the response indicates an error
     */
    private void validateResponse(final HttpResponse<String> response,
                                  final String dataType) throws IOException {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            log.debug("Successfully retrieved {} data", dataType);
            return;
        }

        final String errorMessage = buildErrorMessage(statusCode, response.body(), dataType);
        log.error(errorMessage);

        throw new IOException(errorMessage);
    }

    /**
     * Builds a descriptive error message based on HTTP status code.
     *
     * @param statusCode HTTP status code
     * @param responseBody response body content
     * @param dataType type of data being retrieved
     * @return formatted error message
     */
    private String buildErrorMessage(final int statusCode,
                                     final String responseBody,
                                     final String dataType) {
        final StringBuilder message = new StringBuilder();
        message.append("Failed to retrieve ").append(dataType).append(" data. ");

        switch (statusCode) {
            case HTTP_UNAUTHORIZED:
                message.append("Authentication failed (401)");
                break;
            case HTTP_NOT_FOUND:
                message.append("Endpoint not found (404)");
                break;
            default:
                if (statusCode >= HTTP_SERVER_ERROR) {
                    message.append("Server error (").append(statusCode).append(")");
                } else {
                    message.append("HTTP ").append(statusCode);
                }
        }

        if (responseBody != null && !responseBody.isEmpty()) {
            message.append(" - ").append(responseBody);
        }

        return message.toString();
    }

    /**
     * Parses JSON response to specified type.
     *
     * @param <T> target type
     * @param json JSON string to parse
     * @param clazz target class
     * @return parsed object
     * @throws IOException if parsing fails
     */
    private <T> T parseResponse(final String json, final Class<T> clazz)
            throws IOException {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (final IOException e) {
            log.error("Failed to parse {} response: {}",
                    clazz.getSimpleName(), e.getMessage());
            throw new IOException("Failed to parse response as " + clazz.getSimpleName(), e);
        }
    }
}