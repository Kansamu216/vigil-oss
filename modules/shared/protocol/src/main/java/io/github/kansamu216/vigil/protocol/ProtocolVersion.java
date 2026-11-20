package io.github.kansamu216.vigil.protocol;

public final class ProtocolVersion {

    public static final int MAJOR = 1;

    public static final int MINOR = 0;

    private ProtocolVersion() {
    }

    public static boolean isSupported(int major) {
        return major == MAJOR;
    }

    public static String display() {
        return MAJOR + "." + MINOR;
    }
}
