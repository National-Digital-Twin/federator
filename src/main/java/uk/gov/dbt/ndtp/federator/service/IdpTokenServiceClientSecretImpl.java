
package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

@Slf4j
public class IdpTokenServiceClientSecretImpl extends AbstractIdpTokenService {

    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private final String idpTokenUrl;
    private final String idpClientId;
    private final String idpClientSecret;

    public IdpTokenServiceClientSecretImpl(HttpClient httpClient, ObjectMapper objectMapper) {
        super(PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES).getProperty("idp.jwks.url"), httpClient, objectMapper);
        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        this.idpTokenUrl = properties.getProperty("idp.token.url");
        this.idpClientId = properties.getProperty("idp.client.id");
        this.idpClientSecret = properties.getProperty("idp.client.secret");
    }

    @Override
    public String fetchToken() {
        try {
            String body = GRANT_TYPE + EQUALS_SIGN + CLIENT_CREDENTIALS +
                    AMPERSAND + CLIENT_ID + EQUALS_SIGN + idpClientId +
                    AMPERSAND + CLIENT_SECRET + EQUALS_SIGN + idpClientSecret;
//            String body = GRANT_TYPE + EQUALS_SIGN + CLIENT_CREDENTIALS;
            String auth = idpClientId + ":" + idpClientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(idpTokenUrl))
//                    .header("Authorization", "Basic " + encodedAuth)
                    .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM_URLENCODED)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Failed to fetch token: HTTP {} - {}", response.statusCode(), response.body());
                throw new FederatorTokenException("Failed to fetch token: " + response.body());
            }
            Map<String, Object> json =
                    objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            var accessToken = (String) json.get(ACCESS_TOKEN);
            log.info("Access token fetched successfully");
            return accessToken;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while fetching access token", e);
            throw new FederatorTokenException("Thread interrupted while fetching token from IDP", e);
        } catch (Exception e) {
            log.error("Failed to fetch access token", e);
            throw new FederatorTokenException("Error fetching token from IDP", e);
        }
    }
}