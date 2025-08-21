package uk.gov.dbt.ndtp.federator.utils;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.federator.exceptions.FederatorSslException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SSLUtils covering both positive and negative scenarios.
 */
class SSLUtilsTest {
	
	/**
	 * Creates an in-memory PKCS12 keystore with a self-signed certificate for testing.
	 */
	private static ByteArrayInputStream createMockP12Keystore(String password) throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");
		ks.load(null, null);
		
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		KeyPair kp = kpg.generateKeyPair();
		
		X509Certificate cert = SelfSignedCertificateGenerator.generate("CN=Test", kp, 30);
		
		ks.setKeyEntry("alias", kp.getPrivate(), password.toCharArray(), new java.security.cert.Certificate[]{cert});
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ks.store(baos, password.toCharArray());
		return new ByteArrayInputStream(baos.toByteArray());
	}
	
	/**
	 * Creates an in-memory JKS truststore with a self-signed certificate for testing.
	 */
	private static ByteArrayInputStream createMockJksTruststore(String password) throws Exception {
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(null, null);
		
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		KeyPair kp = kpg.generateKeyPair();
		
		X509Certificate cert = SelfSignedCertificateGenerator.generate("CN=Test", kp, 30);
		
		ks.setCertificateEntry("alias", cert);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ks.store(baos, password.toCharArray());
		return new ByteArrayInputStream(baos.toByteArray());
	}
	
	/**
	 * Negative test: Verifies FederatorSslException is thrown for null arguments to createKeyManagerFromP12.
	 */
	@Test
	void testCreateKeyManagerFromP12_NullArgs_ThrowsFederatorSslException() {
		assertThrows(FederatorSslException.class, () ->
			SSLUtils.createKeyManagerFromP12((InputStream) null, "password"));
		
		ByteArrayInputStream emptyInput = new ByteArrayInputStream(new byte[0]);
		assertThrows(FederatorSslException.class, () ->
			SSLUtils.createKeyManagerFromP12(emptyInput, null));
		
	}
	
	/**
	 * Negative test: Verifies FederatorSslException is thrown for non-existent file in createKeyManagerFromP12.
	 */
	@Test
	void testCreateKeyManagerFromP12_FileNotFound_ThrowsFederatorSslException() {
		FederatorSslException e = assertThrows(FederatorSslException.class, () ->
			SSLUtils.createKeyManagerFromP12("nonexistent.p12", "password"));
		assertTrue(e.getMessage().contains("Client P12 file not found"));
	}
	
	/**
	 * Negative test: Verifies FederatorSslException is thrown for null arguments to createTrustManager.
	 */
	@Test
	void testCreateTrustManager_NullArgs_ThrowsFederatorSslException() {
		FederatorSslException e1 = assertThrows(FederatorSslException.class, () ->
			SSLUtils.createTrustManager((InputStream) null, "password"));
		assertTrue(e1.getMessage().contains("input stream or password is not set."));
		
		ByteArrayInputStream emptyInput = new ByteArrayInputStream(new byte[0]);
		FederatorSslException e2 = assertThrows(FederatorSslException.class, () ->
			SSLUtils.createTrustManager(emptyInput, null));
		assertTrue(e2.getMessage().contains("input stream or password is not set."));
	}
	
	/**
	 * Negative test: Verifies FederatorSslException is thrown for non-existent file in createTrustManager.
	 */
	@Test
	void testCreateTrustManager_FileNotFound_ThrowsFederatorSslException() {
		FederatorSslException e = assertThrows(FederatorSslException.class, () ->
			SSLUtils.createTrustManager("nonexistent.jks", "password"));
		assertTrue(e.getMessage().contains("Trust store file not found"));
	}
	
	
	/**
	 * Positive test: Verifies createKeyManagerFromP12(InputStream, String) returns non-empty KeyManager array for a valid in-memory PKCS12 keystore.
	 */
	@Test
	void testCreateKeyManagerFromP12_InputStream_Positive() throws Exception {
		String password = "testpass";
		try (ByteArrayInputStream p12 = createMockP12Keystore(password)) {
			KeyManager[] kms = SSLUtils.createKeyManagerFromP12(p12, password);
			assertNotNull(kms);
			assertTrue(kms.length > 0);
		}
	}
	
	/**
	 * Positive test: Verifies createTrustManager(InputStream, String) returns non-empty TrustManager array for a valid in-memory JKS truststore.
	 */
	@Test
	void testCreateTrustManager_InputStream_Positive() throws Exception {
		String password = "testpass";
		try (ByteArrayInputStream jks = createMockJksTruststore(password)) {
			TrustManager[] tms = SSLUtils.createTrustManager(jks, password);
			assertNotNull(tms);
			assertTrue(tms.length > 0);
		}
	}
	
	/**
	 * Minimal self-signed certificate generator for test keystores using Bouncy Castle.
	 */
	static class SelfSignedCertificateGenerator {
		static X509Certificate generate(String dn, KeyPair keyPair, int days) throws Exception {
			long now = System.currentTimeMillis();
			Date from = new Date(now);
			Date to = new Date(now + days * 86400000L);
			BigInteger serial = BigInteger.valueOf(now);
			
			X500Name subject = new X500Name(dn);
			X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
				subject, serial, from, to, subject, keyPair.getPublic());
			
			ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
				.build(keyPair.getPrivate());
			
			return new JcaX509CertificateConverter()
				.getCertificate(certBuilder.build(signer));
		}
	}
}