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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.common.utils.PropertyUtil;
import uk.gov.dbt.ndtp.federator.common.utils.ResilienceSupport;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorTokenException;

class IdpTokenServiceTest {

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private IdpTokenService idpTokenService;

    @BeforeEach
    void setUp() {
        PropertyUtil.clear();
        try {
            java.io.File tmp = java.io.File.createTempFile("idp-resilience-test", ".properties");
            tmp.deleteOnExit();
            try (java.io.FileWriter fw = new java.io.FileWriter(tmp)) {
                fw.write(
                        """
                        management.node.resilience.retry.maxAttempts=5
                        management.node.resilience.retry.initialWait=PT0.01S
                        management.node.resilience.retry.maxBackoff=PT0.05S
                        management.node.resilience.circuitBreaker.failureRateThreshold=100
                        management.node.resilience.circuitBreaker.minimumNumberOfCalls=100
                        management.node.resilience.circuitBreaker.slidingWindowSize=10
                        management.node.resilience.circuitBreaker.waitDurationInOpenState=PT1S
                        management.node.resilience.circuitBreaker.permittedNumberOfCallsInHalfOpenState=1
                        """);
            }
            PropertyUtil.init(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ResilienceSupport.clearForTests();

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

    @AfterEach
    void tearDown() {
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
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

    @Test
    void retrySucceedsAfterTransientFailures() {
        TestIdpService service = new TestIdpService(3, "VALID_TOKEN");
        String token = service.fetchTokenWithResilience();
        assertEquals("VALID_TOKEN", token);
        assertTrue(service.getFetchInvocations() >= 1);
    }

    @Test
    void retryExhaustsAndThrows() {
        TestIdpService service = new TestIdpService(10, "NEVER");
        assertThrows(FederatorTokenException.class, service::fetchTokenWithResilience);
        assertTrue(service.getFetchInvocations() >= 1);
    }

    @Test
    void circuitBreakerOpensAndBlocksFurtherCalls() {
        ResilienceSupport.clearForTests();
        PropertyUtil.clear();
        try {
            java.io.File tmp = java.io.File.createTempFile("idp-resilience-test-open", ".properties");
            tmp.deleteOnExit();
            try (java.io.FileWriter fw = new java.io.FileWriter(tmp)) {
                fw.write(
                        """
                        management.node.resilience.retry.maxAttempts=3
                        management.node.resilience.retry.initialWait=PT0.01S
                        management.node.resilience.retry.maxBackoff=PT0.05S
                        management.node.resilience.circuitBreaker.failureRateThreshold=1
                        management.node.resilience.circuitBreaker.minimumNumberOfCalls=1
                        management.node.resilience.circuitBreaker.slidingWindowSize=1
                        management.node.resilience.circuitBreaker.waitDurationInOpenState=PT5M
                        management.node.resilience.circuitBreaker.permittedNumberOfCallsInHalfOpenState=1
                        """);
            }
            PropertyUtil.init(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TestIdpService service = new TestIdpService(10, "NEVER");
        // First call will fail and open the breaker
        assertThrows(FederatorTokenException.class, service::fetchTokenWithResilience);
        int invocationsAfterFirst = service.getFetchInvocations();

        // Second call should be blocked immediately by OPEN breaker (no new fetch invocations)
        assertThrows(FederatorTokenException.class, service::fetchTokenWithResilience);
        assertEquals(invocationsAfterFirst, service.getFetchInvocations());
    }

    @Test
    void verifyTokenResilience() {
        AtomicInteger vInvocations = new AtomicInteger(0);
        AtomicInteger vFailures = new AtomicInteger(2);

        AbstractIdpTokenService service = new AbstractIdpTokenService(null, null, null) {
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
                return super.verifyToken(token);
            }

            @Override
            protected boolean verifyTokenInternal(String token) {
                vInvocations.incrementAndGet();
                if (vFailures.getAndDecrement() > 0) {
                    throw new RuntimeException("transient");
                }
                return true;
            }
        };

        boolean result = service.verifyToken("some-token");
        assertTrue(result);
        assertTrue(vInvocations.get() >= 1);
    }

    static class TestIdpService implements IdpTokenService {
        private final AtomicInteger remainingFailures;
        private final AtomicInteger invocations = new AtomicInteger(0);
        private final String token;

        TestIdpService(int failTimes, String token) {
            this.remainingFailures = new AtomicInteger(failTimes);
            this.token = token;
        }

        int getFetchInvocations() {
            return invocations.get();
        }

        @Override
        public String fetchToken() {
            invocations.incrementAndGet();
            if (remainingFailures.getAndDecrement() > 0) {
                throw new RuntimeException("transient");
            }
            return token;
        }

        @Override
        public String fetchToken(String managementNodeId) {
            return fetchToken();
        }

        @Override
        public boolean verifyToken(String token) {
            return true;
        }
    }
}
