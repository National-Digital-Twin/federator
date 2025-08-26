package uk.gov.dbt.ndtp.federator.connectivity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.management.ManagementNodeDataHandler;
import uk.gov.dbt.ndtp.federator.model.JwtToken;
import uk.gov.dbt.ndtp.federator.model.dto.ConsumerConfigDTO;
import uk.gov.dbt.ndtp.federator.model.dto.ProducerConfigDTO;
import uk.gov.dbt.ndtp.federator.service.JwtTokenService;
import uk.gov.dbt.ndtp.federator.utils.PropertyUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileWriter;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ConfigurationClientTest {

    // PASTE YOUR TOKEN HERE
    private static final String REAL_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJlSGlGT2xQSTBOSnA4bVlJRHVxUnp4czFTX1MzRHAyVFY0cUVtdlhEa21JIn0.eyJleHAiOjE3NTYyMjg4NTAsImlhdCI6MTc1NjIyODU1MCwianRpIjoidHJydGNjOmJlYTFiYTgyLTQ1NWItMjBlMS0xNDc5LTAwMGE5ZDU5M2U3NCIsImlzcyI6Imh0dHBzOi8vbG9jYWxob3N0Ojg0NDMvcmVhbG1zL21hbmFnZW1lbnQtbm9kZSIsImF1ZCI6WyJGRURFUkFUT1JfSEVHIiwibWFuYWdlbWVudC1ub2RlIl0sInN1YiI6IjlkZmE5ZDU5LTRlNzItNGNlNi1hZTc5LWQzOTk3ODA0NDI4NSIsInR5cCI6IkJlYXJlciIsImF6cCI6IkZFREVSQVRPUl9CQ0MiLCJyZXNvdXJjZV9hY2Nlc3MiOnsiRkVERVJBVE9SX0hFRyI6eyJyb2xlcyI6WyJCcm93bmZpZWxkTGFuZEF2YWlsYWJpbGl0eSJdfSwibWFuYWdlbWVudC1ub2RlIjp7InJvbGVzIjpbImFjY2Vzc19wcm9kdWNlcl9jb25maWd1cmF0aW9ucyIsImFjY2Vzc19jb25zdW1lcl9jb25maWd1cmF0aW9ucyJdfX0sInNjb3BlIjoiIiwiY2xpZW50SG9zdCI6IjE3Mi4xOC4wLjEiLCJjbGllbnRBZGRyZXNzIjoiMTcyLjE4LjAuMSIsImNsaWVudF9pZCI6IkZFREVSQVRPUl9CQ0MifQ.EriInDXVzn7KJIjUGuW7B-enjxzZsRuIBSoR15_08X3cotCxALNm1iocKTnE2ITxv7ZR0DROWmNOmDoCcJ5UOmITqndBrXgejn3UXCsXmZKRiBekLVbQ70ILPo8yPxZof-YNHhfNolWZJTymYUOHhG5bB0YxvS5vQebdgf7ADARoxMBQAlrgYM4DkErkIbR6cEAwj4aA5_rPd2Xf61qhxBAa0aRdkxV6qTbN8T3liT88eqiQZqBm4F3o9OFzGF_Okh5hr9D9wr7QjlSzP_plZIIzbYbADLmblFItco-TvbWj_5OujB6vLZgugdTMZraLRtcnix9y7eCdQkC6LCBdWA";

    static {
        try {
            PropertyUtil.clear();
            File props = File.createTempFile("config", ".properties");
            FileWriter w = new FileWriter(props);
            w.write("keycloak.server.url=https://localhost:8443\n");
            w.write("keycloak.realm=management-node\n");
            w.write("keycloak.client.id=FEDERATOR_BCC\n");
            w.write("keycloak.client.secret=dummy\n");
            w.write("keycloak.token.buffer.seconds=60\n");
            w.write("management.node.base.url=https://localhost:8090\n");
            w.write("management.node.request.timeout=30\n");
            w.write("management.node.connectivity.timeout=5\n");
            w.write("management.node.server.error.threshold=500\n");
            w.write("management.node.api.endpoints.producer=/api/v1/configuration/producer\n");
            w.write("management.node.api.endpoints.consumer=/api/v1/configuration/consumer\n");
            w.write("management.node.http.headers.authorization=Authorization\n");
            w.write("management.node.http.headers.bearer.prefix=Bearer \n");
            w.close();
            PropertyUtil.init(props);
        } catch (Exception e) {
            log.error("Property initialization failed", e);
        }
    }

    static class HardcodedTokenService extends JwtTokenService {
        private final String token;

        public HardcodedTokenService(ObjectMapper mapper, String token) {
            super(mapper);
            this.token = token;
        }

        @Override
        public JwtToken fetchJwtToken() {
            try {
                String[] parts = token.split("\\.");
                byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
                Map<String, Object> claims = new ObjectMapper().readValue(payload, Map.class);
                Long exp = ((Number) claims.get("exp")).longValue();
                return JwtToken.builder()
                        .token(token)
                        .tokenType("Bearer")
                        .expiresAt(exp)
                        .clientId((String) claims.getOrDefault("azp", claims.get("client_id")))
                        .claims(claims)
                        .build();
            } catch (Exception e) {
                log.error("Failed to parse token", e);
                throw new IllegalStateException("Invalid token", e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting Configuration Test with Real Token");

        // SSL context
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] c, String s) {}
                    public void checkServerTrusted(X509Certificate[] c, String s) {}
                }
        }, null);

        HttpClient client = HttpClient.newBuilder()
                .sslContext(ssl)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

        JwtTokenService tokenService = new HardcodedTokenService(mapper, REAL_TOKEN);
        ManagementNodeDataHandler handler = new ManagementNodeDataHandler(client, mapper, tokenService);

        // Test connectivity
        log.info("Testing connectivity to Management Node");
        boolean connected = handler.checkConnectivity();
        log.info("Connectivity Status: {}", connected ? "CONNECTED" : "NOT CONNECTED");

        // Test producer configuration
        log.info("Fetching Producer Configuration");
        try {
            ProducerConfigDTO producer = handler.getProducerData(Optional.empty());
            log.info("Producer Configuration Retrieved Successfully");
            log.info("Producer Client ID: {}", producer.getClientId());

            log.info("Full Producer Config JSON:\n{}", mapper.writeValueAsString(producer));
        } catch (Exception e) {
            log.error("Failed to fetch producer configuration", e);
        }

        // Test consumer configuration
        log.info("Fetching Consumer Configuration");
        try {
            ConsumerConfigDTO consumer = handler.getConsumerData(Optional.empty());
            log.info("Consumer Configuration Retrieved Successfully");
            log.info("Consumer Client ID: {}", consumer.getClientId());

            log.info("Full Consumer Config JSON:\n{}", mapper.writeValueAsString(consumer));
        } catch (Exception e) {
            log.error("Failed to fetch consumer configuration", e);
        }

        log.info("Test Complete");
    }
}