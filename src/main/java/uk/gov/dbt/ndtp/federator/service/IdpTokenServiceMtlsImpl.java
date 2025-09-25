// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.utils.RedisUtil;

/**
 * Implementation of IdpTokenService that fetches tokens from an Identity Provider (IDP)
 * using mutual TLS (mTLS) authentication and caches them in Redis.
 */
@Slf4j
public class IdpTokenServiceMtlsImpl extends AbstractIdpTokenService {

    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private static final String MANAGEMENT_NODE_DEFAULT_ID = "default";
    private final long tokenRequestBackoff; // milliseconds
    private final String idpTokenUrl;
    private final String idpClientId;

    public IdpTokenServiceMtlsImpl(HttpClient httpClient, ObjectMapper objectMapper) {
        super(
                PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES).getProperty("idp.jwks.url"),
                httpClient,
                objectMapper);
        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        this.idpTokenUrl = properties.getProperty("idp.token.url");
        this.idpClientId = properties.getProperty("idp.client.id");
        this.tokenRequestBackoff = Long.parseLong(properties.getProperty("idp.token.backoff", "1000"));
    }

    @Override
    public String fetchToken() {
        return fetchToken(null);
    }

    /**
     * Fetches an access token for the specified management node ID.
     * If a valid token is cached in Redis, it is returned. Otherwise, a new token
     * is fetched from the IDP using mTLS authentication and cached.
     *
     * @param managementNodeId The management node identifier (can be null for default)
     * @return The access token as a String
     * @throws FederatorTokenException if there is an error fetching or caching the token
     */
    @Override
    public String fetchToken(String managementNodeId) {

        try {
            String cachedToken = getTokenFromCacheOrNull(managementNodeId);
            if (cachedToken != null) {
                return cachedToken;
            }

            log.debug("No cached token in Redis for management node {}, fetching from IDP", managementNodeId);

            String body =
                    GRANT_TYPE + EQUALS_SIGN + CLIENT_CREDENTIALS + AMPERSAND + CLIENT_ID + EQUALS_SIGN + idpClientId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(idpTokenUrl))
                    .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM_URLENCODED)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.info(
                        "Initial token fetch failed for management node {}. HTTP {}. Retrying once.",
                        managementNodeId,
                        response.statusCode());
                Thread.sleep(tokenRequestBackoff);
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() != 200) {
                log.error(
                        "Failed to fetch token for management node {}: HTTP {} - {}",
                        managementNodeId,
                        response.statusCode(),
                        response.body());
                throw new FederatorTokenException(String.format(
                        "Failed to fetch token for management node %s. Response: %s",
                        managementNodeId, response.body()));
            }

            Map<String, Object> json =
                    objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            String accessToken = (String) json.get(ACCESS_TOKEN);
            long expiresIn = ((Number) json.get("expires_in")).longValue(); // seconds

            if (StringUtils.isBlank(managementNodeId)) {
                log.info("Access token fetched for default management node, persisting to Redis");
            } else {
                log.info("Access token fetched for management node {}, persisting to Redis", managementNodeId);
            }

            persistTokenInCache(managementNodeId, accessToken, expiresIn);

            return accessToken;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while fetching access token for management node {}", managementNodeId, e);
            throw new FederatorTokenException("Thread interrupted while fetching token from IDP", e);
        } catch (Exception e) {
            log.error("Failed to fetch access token for management node {}", managementNodeId, e);
            throw new FederatorTokenException(
                    String.format("Error fetching token from IDP for management node %s", managementNodeId), e);
        }
    }

    private String getTokenFromCacheOrNull(String managementNodeId) {
        String redisKey = getRedisKey(managementNodeId);
        String cachedToken = RedisUtil.getInstance().getValue(redisKey, String.class, true);
        if (cachedToken != null) {
            if (StringUtils.isBlank(managementNodeId)) {
                log.debug("Using cached access token from Redis for default management node");
            } else {
                log.debug("Using cached access token from Redis for management node {}", managementNodeId);
            }
        }
        return cachedToken;
    }

    private void persistTokenInCache(String managementNodeId, String accessToken, long expiresIn) {
        String redisKey = getRedisKey(managementNodeId);
        RedisUtil.getInstance().setValue(redisKey, accessToken, expiresIn);
    }

    private String getRedisKey(String managementNodeId) {
        if (managementNodeId == null || managementNodeId.isBlank()) {
            managementNodeId = MANAGEMENT_NODE_DEFAULT_ID;
        }
        return "management_node_" + managementNodeId + "_access_token";
    }
}
