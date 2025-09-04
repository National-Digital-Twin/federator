package uk.gov.dbt.ndtp.federator.utils;

import java.net.http.HttpClient;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorSslException;

public class HttpClientFactoryUtils {

    private HttpClientFactoryUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static HttpClient createHttpClientWithMtls(Properties properties) {
        try {
            String keystorePath = properties.getProperty("idp.keystore.path");
            String keystorePassword = properties.getProperty("idp.keystore.password");
            String truststorePath = properties.getProperty("idp.truststore.path");
            String truststorePassword = properties.getProperty("idp.truststore.password");

            SSLContext sslContext =
                    SSLUtils.createSSLContext(keystorePath, keystorePassword, truststorePath, truststorePassword);

            return HttpClient.newBuilder().sslContext(sslContext).build();

        } catch (Exception e) {
            throw new FederatorSslException("Failed to create HttpClient with SSL context", e);
        }
    }

    public static HttpClient createHttpClient(Properties properties) {
        try {
            String truststorePath = properties.getProperty("idp.truststore.path");
            String truststorePassword = properties.getProperty("idp.truststore.password");
            SSLContext sslContext =
                    SSLUtils.createSSLContextWithTrustStore(truststorePath, truststorePassword);
            return HttpClient.newBuilder().sslContext(sslContext).build();
        } catch (Exception e) {
            throw new FederatorSslException("Failed to create HttpClient", e);
        }
    }
}
