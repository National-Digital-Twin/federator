// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

@Slf4j
public abstract class AbstractIdpTokenService implements IdpTokenService {

    protected final String idpJwksUrl;
    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;

    protected AbstractIdpTokenService(String idpJwksUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.idpJwksUrl = idpJwksUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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
