package io.github.kansamu216.vigil.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AeadCipher {

    public static final int KEY_BYTES = 32;
    public static final int IV_BYTES = 12;
    public static final int TAG_BITS = 128;
    private static final int SESSION_PREFIX_BYTES = 4;
    private static final int MAX_PLAINTEXT_BYTES = 64 * 1024;

    private AeadCipher() {
    }

    public static byte[] newSessionKey() {
        return Secrets.randomBytes(KEY_BYTES);
    }

    public static byte[] seal(
            byte[] key, byte[] sessionId, int direction, long sequence, byte[] aad, byte[] plaintext)
            throws CryptoException {
        requireKey(key);
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new CryptoException(
                    CryptoException.Reason.INVALID_INPUT, "plaintext too large: " + plaintext.length);
        }
        byte[] iv = deriveIv(sessionId, direction, sequence);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] sealed = cipher.doFinal(plaintext);
            byte[] out = new byte[IV_BYTES + sealed.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(sealed, 0, out, IV_BYTES, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new CryptoException(
                    CryptoException.Reason.INVALID_INPUT, "seal failed", e);
        }
    }

    public static byte[] unseal(
            byte[] key, byte[] sessionId, int direction, long sequence, byte[] aad, byte[] sealed)
            throws CryptoException {
        requireKey(key);
        if (sealed.length < IV_BYTES + TAG_BITS / 8) {
            throw new CryptoException(
                    CryptoException.Reason.INVALID_INPUT, "sealed data too short: " + sealed.length);
        }
        byte[] expectedIv = deriveIv(sessionId, direction, sequence);
        byte[] actualIv = Arrays.copyOfRange(sealed, 0, IV_BYTES);
        if (!Secrets.constantTimeEquals(expectedIv, actualIv)) {
            Secrets.wipe(expectedIv);
            throw new CryptoException(
                    CryptoException.Reason.AUTHENTICATION_FAILED, "iv mismatch (replay or wrong binding)");
        }
        Secrets.wipe(expectedIv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, actualIv));
            cipher.updateAAD(aad);
            return cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (GeneralSecurityException e) {

            throw new CryptoException(
                    CryptoException.Reason.AUTHENTICATION_FAILED, "unseal failed", e);
        }
    }

    static byte[] deriveIv(byte[] sessionId, int direction, long sequence) {
        if (sessionId == null || sessionId.length < SESSION_PREFIX_BYTES) {
            throw new IllegalArgumentException("sessionId must be at least 4 bytes");
        }
        if (direction != 0 && direction != 1) {
            throw new IllegalArgumentException("direction must be 0 or 1");
        }
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(sessionId, 0, iv, 0, SESSION_PREFIX_BYTES);
        iv[4] = (byte) direction;
        for (int i = 0; i < 7; i++) {
            iv[5 + i] = (byte) (sequence >>> (8 * (6 - i)));
        }
        return iv;
    }

    private static void requireKey(byte[] key) throws CryptoException {
        if (key == null || key.length != KEY_BYTES) {
            throw new CryptoException(
                    CryptoException.Reason.INVALID_INPUT, "AES key must be 32 bytes");
        }
    }
}
