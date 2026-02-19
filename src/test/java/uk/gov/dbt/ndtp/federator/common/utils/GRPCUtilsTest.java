/*
 * SPDX-License-Identifier: Apache-2.0
 * © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally
 * attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.ChannelCredentials;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenService;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenServiceClientSecretImpl;
import uk.gov.dbt.ndtp.federator.common.service.idp.IdpTokenServiceMtlsImpl;

class GRPCUtilsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        PropertyUtil.clear();
    }

    @AfterEach
    void tearDown() {
        PropertyUtil.clear();
    }

    @Test
    void testCalculateSha256Checksum_ByteArray() throws Exception {
        byte[] data = "test data".getBytes();
        String checksum = GRPCUtils.calculateSha256Checksum(data);
        assertNotNull(checksum);
        assertEquals(64, checksum.length());

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String expected = GRPCUtils.bytesToHex(md.digest(data));
        assertEquals(expected, checksum);
    }

    @Test
    void testCalculateSha256Checksum_NullByteArray() {
        assertNull(GRPCUtils.calculateSha256Checksum((byte[]) null));
    }

    @Test
    void testCalculateSha256Checksum_Path() throws Exception {
        Path file = tempDir.resolve("test.txt");
        byte[] data = "test data".getBytes();
        Files.write(file, data);
        String checksum = GRPCUtils.calculateSha256Checksum(file);

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String expected = GRPCUtils.bytesToHex(md.digest(data));
        assertEquals(expected, checksum);
    }

    @Test
    void testCalculateSha256Checksum_NullPath() {
        assertNull(GRPCUtils.calculateSha256Checksum((Path) null));
    }

    @Test
    void testBytesToHex() {
        byte[] bytes = new byte[] {0x00, 0x01, 0x0f, (byte) 0xff};
        assertEquals("00010fff", GRPCUtils.bytesToHex(bytes));
    }

    @Test
    void testCreateIdpTokenService_ClientSecret() throws Exception {
        File commonConfig = tempDir.resolve("common.properties").toFile();
        Properties props = new Properties();
        props.setProperty("idp.mtls.enabled", "false");
        try (FileOutputStream out = new FileOutputStream(commonConfig)) {
            props.store(out, null);
        }

        PropertyUtil.init("test.properties"); // Just to have it initialized
        PropertyUtil.getInstance()
                .properties
                .setProperty(GRPCUtils.COMMON_CONFIG_PROPERTIES, commonConfig.getAbsolutePath());

        try (MockedStatic<HttpClientFactoryUtils> factoryMock = mockStatic(HttpClientFactoryUtils.class)) {
            factoryMock
                    .when(() -> HttpClientFactoryUtils.createHttpClient(any()))
                    .thenReturn(mock(java.net.http.HttpClient.class));

            IdpTokenService service = GRPCUtils.createIdpTokenService();
            assertTrue(service instanceof IdpTokenServiceClientSecretImpl);
        }
    }

    @Test
    void testCreateIdpTokenService_Mtls() throws Exception {
        File commonConfig = tempDir.resolve("common_mtls.properties").toFile();
        Properties props = new Properties();
        props.setProperty("idp.mtls.enabled", "true");
        try (FileOutputStream out = new FileOutputStream(commonConfig)) {
            props.store(out, null);
        }

        PropertyUtil.init("test.properties");
        PropertyUtil.getInstance()
                .properties
                .setProperty(GRPCUtils.COMMON_CONFIG_PROPERTIES, commonConfig.getAbsolutePath());

        // Mocking HttpClientFactoryUtils because it tries to create a real client with MTLS
        try (MockedStatic<HttpClientFactoryUtils> factoryMock = mockStatic(HttpClientFactoryUtils.class)) {
            factoryMock
                    .when(() -> HttpClientFactoryUtils.createHttpClientWithMtls(any()))
                    .thenReturn(mock(java.net.http.HttpClient.class));

            IdpTokenService service = GRPCUtils.createIdpTokenService();
            assertTrue(service instanceof IdpTokenServiceMtlsImpl);
        }
    }

    @Test
    void testGenerateChannelCredentials() {
        PropertyUtil.init("test.properties");
        PropertyUtil.getInstance().properties.setProperty("client.p12FilePath", "nonexistent.p12");
        PropertyUtil.getInstance().properties.setProperty("client.p12Password", "pass");
        PropertyUtil.getInstance().properties.setProperty("client.truststoreFilePath", "nonexistent.jks");
        PropertyUtil.getInstance().properties.setProperty("client.truststorePassword", "pass");

        try (MockedStatic<SSLUtils> sslMock = mockStatic(SSLUtils.class)) {
            sslMock.when(() -> SSLUtils.createKeyManagerFromP12(anyString(), anyString()))
                    .thenReturn(new javax.net.ssl.KeyManager[0]);
            sslMock.when(() -> SSLUtils.createTrustManager(anyString(), anyString()))
                    .thenReturn(new javax.net.ssl.TrustManager[0]);

            ChannelCredentials creds = GRPCUtils.generateChannelCredentials();
            assertNotNull(creds);
        }
    }

    @Test
    void testConstructorIsPrivate() throws Exception {
        java.lang.reflect.Constructor<GRPCUtils> constructor = GRPCUtils.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
    }
}
