package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import static org.apache.jena.vocabulary.XSD.*;

@Slf4j
public class IdpTokenServiceImpl implements IdpTokenService {

    private static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private final String idpJwksUrl;
    private final String idpTokenUrl;
    private final String idpClientId;
    private final String idpClientSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public IdpTokenServiceImpl(HttpClient httpClient, ObjectMapper objectMapper) {
        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        this.idpJwksUrl = properties.getProperty("idp.jwks.url");
        this.idpTokenUrl = properties.getProperty("idp.token.url");
        this.idpClientId = properties.getProperty("idp.client.id");
        this.idpClientSecret = properties.getProperty("idp.client.secret");
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String fetchToken() {
        try {
            String body =
                    GRANT_TYPE + EQUALS_SIGN + CLIENT_CREDENTIALS + AMPERSAND + CLIENT_ID + EQUALS_SIGN + idpClientId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(idpTokenUrl))
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


    public String fetchTokenWithClientIdAndSecret() {
        try {
            String body = GRANT_TYPE + EQUALS_SIGN + CLIENT_CREDENTIALS +
                    AMPERSAND + CLIENT_ID + EQUALS_SIGN + idpClientId +
                    AMPERSAND + CLIENT_SECRET + EQUALS_SIGN + idpClientSecret;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(idpTokenUrl))
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

    @Override
    public boolean verifyToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String kid = signedJWT.getHeader().getKeyID();

            JWKSet jwkSet = fetchJwks();

            JWK jwk = selectVerificationKey(jwkSet, kid);

            JWSVerifier verifier = new RSASSAVerifier(((RSAKey) jwk).toRSAPublicKey());
            boolean valid = signedJWT.verify(verifier);
            if (!valid) {
                log.error("Invalid JWT signature for kid {}", kid);
                return false;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims);

            log.info("Token verified successfully. Subject: {}", claims.getSubject());
            return true;

        } catch (ParseException e) {
            log.error("Invalid JWT format", e);
            return false;
        } catch (Exception e) {
            log.error("Token verification failed", e);
            return false;
        }
    }

    private JWKSet fetchJwks() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create(idpJwksUrl)).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("Failed to fetch JWKS: HTTP {} - {}", response.statusCode(), response.body());
            throw new FederatorTokenException("Failed to fetch JWKS: " + response.body());
        }
        return JWKSet.parse(response.body());
    }

    private JWK selectVerificationKey(JWKSet jwkSet, String kid) {
        JWKSelector selector = new JWKSelector(new JWKMatcher.Builder()
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build());

        var matches = selector.select(jwkSet);
        if (matches.isEmpty()) {
            throw new FederatorTokenException("No JWKS key found for kid: " + kid);
        }
        return matches.get(0);
    }

    private void validateClaims(JWTClaimsSet claims) {
        Date exp = claims.getExpirationTime();
        if (exp == null || exp.toInstant().isBefore(Instant.now())) {

            log.error("Token is expired on {} for claim: {}", exp, claims.toJSONObject());

            throw new FederatorTokenException("Token is expired");
        }
        log.debug("Claims validated successfully: {}", claims.toJSONObject());
    }
}
