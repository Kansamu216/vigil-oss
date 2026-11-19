package io.github.kansamu216.vigil.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 随机数与秘密擦除。
 *
 * <p>{@link SecureRandom} 初始化成本高，这里复用单例；密钥材料用完必须显式擦除。
 * 单例只用于取随机字节，不保存任何秘密。</p>
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

    /** 覆盖数组内容。调用方仍需丢弃引用，本方法只降低被转储的概率。 */
    public static void wipe(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /**
     * 恒定时间比较，避免通过响应时间区分“前缀相同”和“完全不同”。
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
