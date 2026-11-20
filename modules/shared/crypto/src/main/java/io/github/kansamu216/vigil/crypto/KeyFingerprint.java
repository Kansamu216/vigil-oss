package io.github.kansamu216.vigil.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

/**
 * 公钥指纹（SHA-256，小写十六进制）。
 *
 * <p>客户端只持指纹，不持公钥本身；指纹不同即视为不同交付，直接拒绝连接。</p>
 */
public final class KeyFingerprint {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private KeyFingerprint() {
    }

    public static String of(PublicKey publicKey) {
        return of(publicKey.getEncoded());
    }

    public static String of(byte[] encodedKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(encodedKey));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }
}
