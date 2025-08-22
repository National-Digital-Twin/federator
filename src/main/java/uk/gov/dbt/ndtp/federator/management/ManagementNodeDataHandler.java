package uk.gov.dbt.ndtp.federator.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Handler for retrieving consumer and producer configurations from the Management Node.
 * Provides secure access to configuration data using JWT authentication.
 *
 * @author Rakesh Chiluka
 * @version 3.0
 * @since 2025-01-20
 */
@Slf4j
public class ManagementNodeDataHandler {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PRODUCER_ENDPOINT = "/api/v1/configuration/producer";
    private static final String CONSUMER_ENDPOINT = "/api/v1/configuration/consumer";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int HTTP_OK = 200;

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
     */
    public ManagementNodeDataHandler(final HttpClient httpClient,
                                     final ObjectMapper objectMapper,
                                     final String managementNodeBaseUrl,
                                     final JwtTokenService tokenService,
                                     final Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.managementNodeBaseUrl = validateBaseUrl(managementNodeBaseUrl);
        this.tokenService = Objects.requireNonNull(tokenService, "JwtTokenService cannot be null");
        this.requestTimeout = Objects.requireNonNullElse(requestTimeout, DEFAULT_TIMEOUT);
        log.info("ManagementNodeDataHandler initialized with base URL: {}", managementNodeBaseUrl);
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
     * @return ProducerConfigDTO object containing producer configuration
     * @throws IOException if the network request fails
     */
    public ProducerConfigDTO getProducerData() throws IOException {
        final String jwtToken = fetchAndValidateToken();
        final String url = managementNodeBaseUrl + PRODUCER_ENDPOINT;
        log.debug("Fetching producer data from: {}", url);

        final HttpResponse<String> response = executeRequest(url, jwtToken);
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("Failed to retrieve producer data. HTTP " + response.statusCode());
        }

        final ProducerConfigDTO config = objectMapper.readValue(response.body(), ProducerConfigDTO.class);
        log.info("Successfully retrieved producer configuration for client: {}", config.getClientId());
        return config;
    }

    /**
     * Retrieves consumer configuration data from the management node.
     *
     * @return ConsumerConfigDTO object containing consumer configuration
     * @throws IOException if the network request fails
     */
    public ConsumerConfigDTO getConsumerData() throws IOException {
        final String jwtToken = fetchAndValidateToken();
        final String url = managementNodeBaseUrl + CONSUMER_ENDPOINT;
        log.debug("Fetching consumer data from: {}", url);

        final HttpResponse<String> response = executeRequest(url, jwtToken);
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("Failed to retrieve consumer data. HTTP " + response.statusCode());
        }

        final ConsumerConfigDTO config = objectMapper.readValue(response.body(), ConsumerConfigDTO.class);
        log.info("Successfully retrieved consumer configuration for client: {}", config.getClientId());
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
            final boolean reachable = response.statusCode() < 500;
            log.info("Management node {} at: {}",
                    reachable ? "reachable" : "unreachable", managementNodeBaseUrl);
            return reachable;
        } catch (final Exception e) {
            log.error("Failed to connect to management node: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetches and validates JWT token.
     *
     * @return valid JWT token
     * @throws IOException if token fetch or validation fails
     */
    private String fetchAndValidateToken() throws IOException {
        final String token = tokenService.fetchJwtToken();
        if (token == null || token.trim().isEmpty()) {
            throw new IOException("Failed to obtain JWT token");
        }
        if (!tokenService.isTokenValid(token)) {
            throw new IOException("JWT token is invalid or expired");
        }
        final long remaining = tokenService.getRemainingValidity(token);
        log.debug("Token valid for {} more seconds", remaining);
        return token;
    }

    /**
     * Executes HTTP request to the management node.
     *
     * @param url target URL
     * @param jwtToken JWT token for authentication
     * @return HTTP response
     * @throws IOException if request fails
     */
    private HttpResponse<String> executeRequest(final String url, final String jwtToken)
            throws IOException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + jwtToken)
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
     * Validates the base URL format.
     *
     * @param baseUrl the base URL to validate
     * @return the validated base URL (without trailing slash)
     */
    private String validateBaseUrl(final String baseUrl) {
        Objects.requireNonNull(baseUrl, "Base URL cannot be null");
        final String trimmedUrl = baseUrl.trim();
        if (trimmedUrl.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be empty");
        }
        return trimmedUrl.endsWith("/") ?
                trimmedUrl.substring(0, trimmedUrl.length() - 1) : trimmedUrl;
    }
}