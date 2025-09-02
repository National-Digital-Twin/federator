// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

package uk.gov.dbt.ndtp.federator.utils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for AES-GCM encryption and decryption with Base64-encoded keys and data.
 * This class provides methods to encrypt and decrypt UTF-8 text using AES in GCM mode with no padding.
 * The AES key is provided as a Base64-encoded string, and the encrypted output is also Base64-encoded.
 * <p>
 **/
public class AesCryptoUtil {

    private static final String AES = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final SecureRandom RNG = new SecureRandom();

    private AesCryptoUtil() {}

    /**
     * Encrypts UTF-8 text with AES-GCM using a Base64 key.
     * <p>
     * Output is a Base64 string of {@code IV || ciphertext || tag}.
     * </p>
     *
     * @param plainText the UTF-8 text to encrypt
     * @param base64Key the AES key in Base64. Must decode to 16, 24, or 32 bytes
     * @return Base64 of {@code IV || ciphertext || tag}
     * @throws IllegalArgumentException if the key is blank or has an invalid length
     * @throws IllegalStateException if the cryptographic operation fails
     */
    public static String encrypt(String plainText, String base64Key) {
        SecretKey key = keyFromBase64(base64Key);
        return encryptToBase64(plainText, key);
    }

    /**
     * Decrypts a Base64 string produced by {@link #encrypt(String, String)}.
     * <p>
     * Input must be Base64 of {@code IV || ciphertext || tag}.
     * </p>
     *
     * @param base64CipherText Base64 of {@code IV || ciphertext || tag}
     * @param base64Key        the AES key in Base64. Must decode to 16, 24, or 32 bytes
     * @return the decrypted UTF-8 text
     * @throws IllegalArgumentException if input is too short or the key is invalid
     * @throws IllegalStateException if decryption or authentication fails
     */
    public static String decrypt(String base64CipherText, String base64Key) {
        SecretKey key = keyFromBase64(base64Key);
        return decryptFromBase64(base64CipherText, key);
    }

    /**
     * Builds a {@link SecretKey} from a Base64 string.
     *
     * @param base64Key Base64-encoded AES key
     * @return the SecretKey instance
     * @throws IllegalArgumentException if the key is blank or not 16, 24, or 32 bytes after decoding
     */
    private static SecretKey keyFromBase64(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("key is blank");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        int decodedKeyLength = keyBytes.length;
        if (decodedKeyLength != 16 && decodedKeyLength != 24 && decodedKeyLength != 32) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes. Got " + decodedKeyLength);
        }
        return new SecretKeySpec(keyBytes, AES);
    }

    /**
     * Encrypts text and returns Base64 of {@code IV || ciphertext || tag}.
     *
     * @param plainText UTF-8 text
     * @param key       AES secret key
     * @return Base64-encoded {@code IV || ciphertext || tag}
     * @throws IllegalStateException if encryption fails
     */
    private static String encryptToBase64(String plainText, SecretKey key) {
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    /**
     * Decrypts Base64 of {@code IV || ciphertext || tag} and returns UTF-8 text.
     *
     * @param base64Data Base64-encoded {@code IV || ciphertext || tag}
     * @param key        AES secret key
     * @return plaintext as UTF-8
     * @throws IllegalArgumentException if the input is too short
     * @throws IllegalStateException if decryption or authentication fails
     */
    private static String decryptFromBase64(String base64Data, SecretKey key) {
        try {
            byte[] all = Base64.getDecoder().decode(base64Data);
            if (all.length < IV_LEN + 1) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }
}
