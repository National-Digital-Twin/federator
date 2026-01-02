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
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

/**
 * Implementation of IdpTokenService that fetches tokens from an Identity Provider (IDP)
 * using client secret authentication.
 */
@Slf4j
public class IdpTokenServiceClientSecretImpl extends AbstractIdpTokenService {

    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private final String idpTokenUrl;
    private final String idpClientId;
    private final String idpClientSecret;

    public IdpTokenServiceClientSecretImpl(HttpClient httpClient, ObjectMapper objectMapper) {
        super(
                PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES).getProperty("idp.jwks.url"),
                httpClient,
                objectMapper);
        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        this.idpTokenUrl = properties.getProperty("idp.token.url");
        this.idpClientId = properties.getProperty("idp.client.id");
        this.idpClientSecret = properties.getProperty("idp.client.secret");
    }

    /**
     * Fetches an access token using client credentials.
     *
     * @return The access token as a String
     * @throws FederatorTokenException if there is an error fetching the token
     */
    @Override
    public String fetchToken() {
        return fetchTokenWithResilience();
    }

    /**
     * Fetches an access token for the specified management node ID using client credentials.
     *
     * @param managementNodeId The management node identifier (can be null for default)
     * @return The access token as a String
     * @throws FederatorTokenException if there is an error fetching the token
     */
    @Override
    public String fetchToken(String managementNodeId) {
        return fetchTokenInternal(managementNodeId);
    }

    private String fetchTokenInternal(String managementNodeId) {
        try {
            String body = GRANT_TYPE
                    + EQUALS_SIGN
                    + CLIENT_CREDENTIALS
                    + AMPERSAND
                    + CLIENT_ID
                    + EQUALS_SIGN
                    + idpClientId
                    + AMPERSAND
                    + CLIENT_SECRET
                    + EQUALS_SIGN
                    + idpClientSecret;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(idpTokenUrl))
                    .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM_URLENCODED)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new FederatorTokenException(
                        String.format("Failed to fetch token: HTTP %d - %s", response.statusCode(), response.body()));
            }
            Map<String, Object> json =
                    objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            var accessToken = (String) json.get(ACCESS_TOKEN);
            log.info("Access token fetched successfully");
            return accessToken;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FederatorTokenException("Thread interrupted while fetching token from IDP", e);
        } catch (Exception e) {
            throw new FederatorTokenException("Error fetching token from IDP", e);
        }
    }
}
