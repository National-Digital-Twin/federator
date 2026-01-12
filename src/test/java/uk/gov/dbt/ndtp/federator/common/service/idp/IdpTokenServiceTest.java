/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.service.idp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

class IdpTokenServiceTest {

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private IdpTokenService idpTokenService;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        objectMapper = new ObjectMapper();
        // Anonymous implementation to test default methods in interface
        idpTokenService = new IdpTokenService() {
            @Override
            public String fetchToken() {
                return null;
            }

            @Override
            public String fetchToken(String managementNodeId) {
                return null;
            }

            @Override
            public boolean verifyToken(String token) {
                return false;
            }
        };
    }

    @Test
    void testExtractClientIdFromToken_invalid() {
        assertThrows(FederatorTokenException.class, () -> idpTokenService.extractClientIdFromToken("invalid"));
    }

    @Test
    void testExtractAudiencesFromToken_invalid() {
        assertThrows(FederatorTokenException.class, () -> idpTokenService.extractAudiencesFromToken("invalid"));
    }

    @Test
    void testAbstractIdpTokenService_fetchJwks_failure() throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn("Not Found");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        AbstractIdpTokenService service = new AbstractIdpTokenService("http://jwks", httpClient, objectMapper) {
            @Override
            public String fetchToken() {
                return null;
            }

            @Override
            public String fetchToken(String managementNodeId) {
                return null;
            }
        };

        assertFalse(service.verifyToken("some.token.here"));
    }
}
