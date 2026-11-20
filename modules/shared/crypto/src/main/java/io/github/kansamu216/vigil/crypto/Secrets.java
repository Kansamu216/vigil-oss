package io.github.kansamu216.vigil.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 随机数与秘密擦除。
 *
 * <p>复用 {@link SecureRandom} 单例；密钥材料用完应显式擦除。</p>
 */
public final class Secrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Secrets() {
    }

    public static byte[] randomBytes(int length) {
        if (length <= 0 || length > 1024) {
            throw new IllegalArgumentException("invalid random length: " + length);
        }
        byte[] out = new byte[length];
        RANDOM.nextBytes(out);
        return out;
    }

    /** 覆盖数组内容；调用方仍需丢弃引用。 */
    public static void wipe(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /**
     * 按恒定时间比较字节数组。
     */
    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left == null || right == null || left.length != right.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }
}
