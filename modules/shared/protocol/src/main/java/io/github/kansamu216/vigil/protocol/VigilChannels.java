package io.github.kansamu216.vigil.protocol;

public final class VigilChannels {

    private static final String NAMESPACE = "vigil";

    private static final String PING = "ping";

    private static final String FRAME = "frame";

    private static final int PROTOCOL_VERSION = 1;

    private VigilChannels() {
    }

    public static String namespace() {
        return NAMESPACE;
    }

    public static String pingId() {
        return PING;
    }

    public static String frameId() {
        return FRAME;
    }

    public static int protocolVersion() {
        return PROTOCOL_VERSION;
    }
}
