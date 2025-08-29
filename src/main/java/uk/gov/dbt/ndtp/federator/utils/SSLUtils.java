// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */
package uk.gov.dbt.ndtp.federator.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorSslException;

/**
 * Utility class for creating SSL KeyManager and TrustManager instances from PKCS12 and JKS keystores.
 */
@Slf4j
public class SSLUtils {

    private SSLUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates KeyManagers from a PKCS12 (.p12) file.
     *
     * @param p12FilePath the path to the PKCS12 file
     * @param password    the password for the keystore
     * @return an array of KeyManagers
     * @throws FederatorSslException if the file is not found or cannot be loaded
     */
    public static KeyManager[] createKeyManagerFromP12(String p12FilePath, String password) {
        try (InputStream inputStream = new FileInputStream(p12FilePath)) {
            return createKeyManagerFromP12(inputStream, password);
        } catch (IOException e) {
            throw new FederatorSslException("Client P12 file not found: " + p12FilePath, e);
        }
    }

    /**
     * Creates KeyManagers from a PKCS12 (.p12) input stream.
     *
     * @param p12InputStream the input stream of the PKCS12 file
     * @param password       the password for the keystore
     * @return an array of KeyManagers
     * @throws FederatorSslException if the input stream is null or cannot be loaded
     */
    public static KeyManager[] createKeyManagerFromP12(InputStream p12InputStream, String password) {
        if (p12InputStream == null || password == null) {
            throw new FederatorSslException("Client P12 input stream or password is not set.");
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(p12InputStream, password.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password.toCharArray());
            return kmf.getKeyManagers();
        } catch (IOException | GeneralSecurityException e) {
            throw new FederatorSslException("Failed to load client P12 keystore.", e);
        }
    }

    /**
     * Creates TrustManagers from a JKS file.
     *
     * @param trustStoreFilePath the path to the JKS truststore file
     * @param trustStorePassword the password for the truststore
     * @return an array of TrustManagers
     * @throws FederatorSslException if the file is not found or cannot be loaded
     */
    public static TrustManager[] createTrustManager(String trustStoreFilePath, String trustStorePassword) {
        try (InputStream inputStream = new FileInputStream(trustStoreFilePath)) {
            return createTrustManager(inputStream, trustStorePassword);
        } catch (IOException e) {
            throw new FederatorSslException("Trust store file not found: " + trustStoreFilePath, e);
        }
    }

    /**
     * Creates TrustManagers from a JKS input stream.
     *
     * @param trustStoreInputStream the input stream of the JKS truststore file
     * @param trustStorePassword    the password for the truststore
     * @return an array of TrustManagers
     * @throws FederatorSslException if the input stream is null or cannot be loaded
     */
    public static TrustManager[] createTrustManager(InputStream trustStoreInputStream, String trustStorePassword) {
        if (trustStoreInputStream == null || trustStorePassword == null) {
            throw new FederatorSslException("Trust store input stream or password is not set.");
        }
        try {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(trustStoreInputStream, trustStorePassword.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf.getTrustManagers();
        } catch (IOException | GeneralSecurityException e) {
            throw new FederatorSslException("Failed to load trust store keystore.", e);
        }
    }

    /**
     * Creates an SSLContext using the provided keystore and truststore paths and passwords.
     *
     * @param keystorePath the path to the PKCS12 keystore file
     * @param keystorePassword the password for the keystore
     * @param truststorePath the path to the JKS truststore file
     * @param truststorePassword the password for the truststore
     * @return an initialized SSLContext
     * */
    public static SSLContext createSSLContext(
            String keystorePath, String keystorePassword, String truststorePath, String truststorePassword) {
        try {
            // Load client keystore (PKCS12)
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(keystorePath)) {
                keyStore.load(is, keystorePassword.toCharArray());
            }

            printCertificates("Keystore certificates:", keyStore);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());

            // Load truststore (JKS)
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (InputStream is = new FileInputStream(truststorePath)) {
                trustStore.load(is, truststorePassword.toCharArray());
            }

            printCertificates("Truststore certificates:", trustStore);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new FederatorSslException("Failed to create SSLContext.", e);
        }
    }

    /**
     * Prints certificate information for all aliases in the given KeyStore.
     */
    private static void printCertificates(String title, KeyStore keyStore) {
        try {
            log.info(title);
            java.util.Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                java.security.cert.Certificate cert = keyStore.getCertificate(alias);
                if (cert != null) {
                    log.info("Alias: {} Certificate: {} ", alias, cert.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to print certificates:", e);
        }
    }
}
