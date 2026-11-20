package io.github.kansamu216.vigil.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

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

    public static void wipe(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }

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
