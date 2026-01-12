package uk.gov.dbt.ndtp.federator.common.service.idp;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.utils.ResilienceSupport;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

/**
 * Defines contract for interacting with an IDP: fetching and verifying tokens.
 */
public interface IdpTokenService {

    String GRANT_TYPE = "grant_type";
    String CLIENT_ID = "client_id";
    String AUTHORIZED_PARTY = "azp";
    String ACCESS_TOKEN = "access_token";
    String CLIENT_SECRET = "client_secret";

    // OAuth2 Grant Types
    String CLIENT_CREDENTIALS = "client_credentials";

    // URL encoding separators
    String EQUALS_SIGN = "=";
    String AMPERSAND = "&";

    // HTTP request constants
    String HEADER_CONTENT_TYPE = "Content-Type";
    String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";
    Logger log = LoggerFactory.getLogger(IdpTokenService.class);

    @SuppressWarnings("java:S2139") // Rethrow exception after logging
    default String extractClientIdFromToken(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getStringClaim(AUTHORIZED_PARTY);
        } catch (ParseException e) {
            String errorMessage = String.format(
                    "Failed to parse accessToken to extract authorized party. Token: %s", maskToken(token));
            log.error(errorMessage, e);
            throw new FederatorTokenException(errorMessage, e);
        }
    }

    @SuppressWarnings("java:S1166") // Sonar: Either log or rethrow
    default List<String> extractAudiencesFromToken(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getAudience();
        } catch (ParseException e) {
            String errorMessage =
                    String.format("Failed to parse accessToken to extract audiences. Token: %s", maskToken(token));
            throw new FederatorTokenException(errorMessage, e);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "invalid-token";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    /**
     * Fetch an access token from the default management node IDP.
     */
    String fetchToken();

    /**
     * Fetch an access token from the IDP associated with a specific identifier..
     * @param managementNodeId The management node ID for which the token is being fetched.
     */
    String fetchToken(String managementNodeId);

    /**
     * Fetch an access token protected by Resilience4j retry and circuit breaker.
     * @param managementNodeId The management node identifier (can be null for default)
     * @return The access token as a String
     */
    default String fetchTokenWithResilience(String managementNodeId) {
        final String componentName = "idp-token-service";
        Supplier<String> supplier = () -> fetchToken(managementNodeId);
        try {
            return ResilienceSupport.decorateAndExecute(componentName, supplier);
        } catch (RuntimeException ex) {
            throw new FederatorTokenException(
                    "Failed to fetch token after resilience protections for management node: " + managementNodeId, ex);
        }
    }

    /**
     * Fetch an access token from the default management node IDP protected by Resilience4j.
     * @return The access token as a String
     */
    default String fetchTokenWithResilience() {
        return fetchTokenWithResilience(null);
    }

    /**
     * Verify an access token against the IDP's JWKS.
     */
    boolean verifyToken(String token);
}
