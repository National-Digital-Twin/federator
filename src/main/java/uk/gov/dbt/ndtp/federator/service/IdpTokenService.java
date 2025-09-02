package uk.gov.dbt.ndtp.federator.service;

/**
 * Defines contract for interacting with an IDP: fetching and verifying tokens.
 */
public interface IdpTokenService {

    String GRANT_TYPE = "grant_type";
    String CLIENT_ID = "client_id";
    String ACCESS_TOKEN = "access_token";

    // OAuth2 Grant Types
    String CLIENT_CREDENTIALS = "client_credentials";

    // URL encoding separators
    String EQUALS_SIGN = "=";
    String AMPERSAND = "&";

    // HTTP request constants
    String HEADER_CONTENT_TYPE = "Content-Type";
    String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";

    /**
     * Fetch an access token from the default management node IDP.
     * @param managementNodeId The management node ID for which the token is being fetched.
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
