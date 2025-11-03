// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced,
// and maintained by the National Digital Twin Programme.
package uk.gov.dbt.ndtp.federator.common.utils;

import static uk.gov.dbt.ndtp.federator.client.grpc.GRPCAbstractClient.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ChannelCredentials;
import io.grpc.TlsChannelCredentials;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Properties;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenServiceClientSecretImpl;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenServiceMtlsImpl;

public class GRPCUtils {
    public static final String COMMON_CONFIG_PROPERTIES = "common.configuration";
    private static final String IDP_MTLS_ENABLED_PROPERTY = "idp.mtls.enabled";

    private static final Logger LOGGER = LoggerFactory.getLogger("GRPCUtils");
    public static final String SHA_256 = "SHA-256";

    private GRPCUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static IdpTokenService createIdpTokenService() {

        Properties properties = PropertyUtil.getPropertiesFromFilePath(COMMON_CONFIG_PROPERTIES);
        boolean isMtlsEnabled = Boolean.parseBoolean(properties.getProperty(IDP_MTLS_ENABLED_PROPERTY, "false"));
        LOGGER.warn("===========Idp mTLS enabled: {}============", isMtlsEnabled);

        HttpClient client = isMtlsEnabled
                ? HttpClientFactoryUtils.createHttpClientWithMtls(properties)
                : HttpClientFactoryUtils.createHttpClient(properties);

        ObjectMapper mapper = ObjectMapperUtil.getInstance();
        return isMtlsEnabled
                ? new IdpTokenServiceMtlsImpl(client, mapper)
                : new IdpTokenServiceClientSecretImpl(client, mapper);
    }

    /**
     * Generate ChannelCredentials with mTLS using client P12 and truststore
     */
    public static ChannelCredentials generateChannelCredentials() {
        return TlsChannelCredentials.newBuilder()
                .keyManager(createKeyManagerFromP12())
                .trustManager(createTrustManager())
                .build();
    }

    public static String calculateSha256Checksum(String input) {
        if (input == null) {
            return null;
        }
        return calculateSha256Checksum(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String calculateSha256Checksum(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(SHA_256);
            byte[] digest = md.digest(data);
            return bytesToHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String calculateSha256Checksum(Path file) {
        if (file == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(SHA_256);
            // Stream the file to avoid loading into memory
            try (var is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256 checksum for file: " + file, e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static KeyManager[] createKeyManagerFromP12() {
        String clientP12FilePath = PropertyUtil.getPropertyValue(CLIENT_P12_FILE_PATH);
        String password = PropertyUtil.getPropertyValue(CLIENT_P12_PASSWORD);
        // log info for filepath and boolean that password in not null
        LOGGER.info(
                "Creating KeyManager with clientP12FilePath: {}, password: {}",
                clientP12FilePath,
                password != null ? "******" : "null");

        return SSLUtils.createKeyManagerFromP12(clientP12FilePath, password);
    }

    /**
     * Create TrustManagerFactory from JKS file path
     */
    private static TrustManager[] createTrustManager() {
        String trustStoreFilePath = PropertyUtil.getPropertyValue(CLIENT_TRUSTSTORE_FILE_PATH);
        String trustStorePassword = PropertyUtil.getPropertyValue(CLIENT_TRUSTSTORE_PASSWORD);

        LOGGER.info(
                "Creating TrustManager with trustStoreFilePath: {}, trustStorePassword: {}",
                trustStoreFilePath,
                trustStorePassword != null ? "******" : "null");
        return SSLUtils.createTrustManager(trustStoreFilePath, trustStorePassword);
    }
}
