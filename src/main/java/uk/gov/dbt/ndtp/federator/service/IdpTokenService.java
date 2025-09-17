package uk.gov.dbt.ndtp.federator.service;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

/**
 * Defines contract for interacting with an IDP: fetching and verifying tokens.
 */
public interface IdpTokenService {

    String GRANT_TYPE = "grant_type";
    String CLIENT_ID = "client_id";
    String ACCESS_TOKEN = "access_token";
    String CLIENT_SECRET = "client_secret";
    String AUTHORIZED_PARTY = "azp";

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
            String errorMessage =
                    String.format("Failed to parse accessToken to extract client_id. Token: %s", maskToken(token));
            log.error(errorMessage, e);
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
     * Verify an access token against the IDP's JWKS.
     */
    boolean verifyToken(String token);
}
