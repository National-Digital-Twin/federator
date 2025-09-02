// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

package uk.gov.dbt.ndtp.federator.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesCryptoUtilsTest {

    // 16, 24, 32 bytes
    private static final String KEY_128 = "ET3igtDKRGp3+wrPhaSnXg==";
    private static final String KEY_192 = "IgMvqWr9/aKEOHpx1/p+0JmISgbubU+I";
    private static final String KEY_256 = "Bg7HhP2hl/lqYeri2BAV5dTVOg81FgfBqZzFhPLjVXE=";

    @Test
    void roundTrip_128bit_plainAscii() {
        String pt = "hello world";
        String ct = AesCryptoUtils.encrypt(pt, KEY_128);
        String out = AesCryptoUtils.decrypt(ct, KEY_128);
        assertEquals(pt, out);
    }

    @Test
    void roundTrip_192bit_nonAscii() {
        String pt = "£€漢字";
        String ct = AesCryptoUtils.encrypt(pt, KEY_192);
        String out = AesCryptoUtils.decrypt(ct, KEY_192);
        assertEquals(pt, out);
    }

    @Test
    void roundTrip_256bit_emptyString() {
        String pt = "";
        String ct = AesCryptoUtils.encrypt(pt, KEY_256);
        String out = AesCryptoUtils.decrypt(ct, KEY_256);
        assertEquals(pt, out);
    }

    @Test
    void encrypt_producesDifferentCiphertexts_dueToRandomIv() {
        String pt = "same text";
        String c1 = AesCryptoUtils.encrypt(pt, KEY_256);
        String c2 = AesCryptoUtils.encrypt(pt, KEY_256);
        assertNotEquals(c1, c2);
    }

    @Test
    void ciphertext_isBase64_andContainsIvPrefix() {
        String pt = "check structure";
        String ct = AesCryptoUtils.encrypt(pt, KEY_128);
        byte[] decoded = Base64.getDecoder().decode(ct);
        // IV (12) + tag (16) + ciphertext (>=0)
        assertTrue(decoded.length >= 12 + 16);
    }

    @Test
    void decrypt_withWrongKey_fails() {
        String pt = "secret";
        String ct = AesCryptoUtils.encrypt(pt, KEY_128);
        assertThrows(IllegalStateException.class, () -> AesCryptoUtils.decrypt(ct, KEY_256));
    }

    @Test
    void encrypt_withBlankKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> AesCryptoUtils.encrypt("x", ""));
    }

    @Test
    void encrypt_withBadLengthKey_throws() {
        // 15-byte key (invalid)
        String badKey = Base64.getEncoder().encodeToString(new byte[15]);
        assertThrows(IllegalArgumentException.class, () -> AesCryptoUtils.encrypt("x", badKey));
    }

    @Test
    void decrypt_shortCiphertext_fails() {
        // Base64 of 2 bytes, shorter than IV
        String shortCt = "AA==";
        assertThrows(IllegalStateException.class, () -> AesCryptoUtils.decrypt(shortCt, KEY_128));
    }

    @Test
    void decrypt_badBase64_fails() {
        String notB64 = "%%%not-base64%%%";
        assertThrows(IllegalStateException.class, () -> AesCryptoUtils.decrypt(notB64, KEY_128));
    }

    @Test
    void tamper_ciphertext_failsAuth() {
        String pt = "auth check";
        String ct = AesCryptoUtils.encrypt(pt, KEY_256);
        byte[] bytes = Base64.getDecoder().decode(ct);
        bytes[bytes.length - 1] ^= 0x01; // flip last bit
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> AesCryptoUtils.decrypt(tampered, KEY_256));
    }
}
