// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.service.idp;

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
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.RedisUtil;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

/**
 * Implementation of IdpTokenService that fetches tokens from an Identity Provider (IDP)
 * using mutual TLS (mTLS) authentication and caches them in Redis.
 */
@Slf4j
public class IdpTokenServiceMtlsImpl extends AbstractIdpTokenService {

    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private static final String MANAGEMENT_NODE_DEFAULT_ID = "default";
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
    }

    @Override
    public String fetchToken() {
        return fetchTokenWithResilience();
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
        return fetchTokenInternal(managementNodeId);
    }

    private String fetchTokenInternal(String managementNodeId) {

        try {
            String cachedToken = getTokenFromCacheOrNull(managementNodeId);
            if (cachedToken != null) {
                return cachedToken;
            }

            if (StringUtils.isBlank(managementNodeId)) {
                log.debug("No cached token in Redis for default management node, fetching from IDP");
            } else {
                log.debug("No cached token in Redis for management node {}, fetching from IDP", managementNodeId);
            }

            String body =
                    GRANT_TYPE + EQUALS_SIGN + CLIENT_CREDENTIALS + AMPERSAND + CLIENT_ID + EQUALS_SIGN + idpClientId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(idpTokenUrl))
                    .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM_URLENCODED)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            log.debug("attempting to fetch token for management node {}", idpTokenUrl);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
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
            throw new FederatorTokenException("Thread interrupted while fetching token from IDP", e);
        } catch (Exception e) {
            log.error("Error fetching token from IDP, {}", e.getMessage());
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
