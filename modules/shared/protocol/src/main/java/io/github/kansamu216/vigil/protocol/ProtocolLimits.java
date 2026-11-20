package io.github.kansamu216.vigil.protocol;

public final class ProtocolLimits {

    public static final int ENVELOPE_HEADER_BYTES = 2 + 2 + 1 + 8 + 2;

    public static final int PAYLOAD_LENGTH_OFFSET = 2 + 2 + 1 + 8;

    public static final int MAX_PAYLOAD_BYTES = 32 * 1024;

    public static final int MAX_FRAME_BYTES = ENVELOPE_HEADER_BYTES + MAX_PAYLOAD_BYTES;

    public static final int MAX_STRING_BYTES = 512;

    public static final int MAX_MOD_ENTRIES = 2048;

    public static final int MAX_OBSERVATIONS = 256;

    public static final int MAX_SCREEN_CHUNKS = 512;

    public static final int MAX_SCREEN_TOTAL_BYTES = 8 * 1024 * 1024;

    private ProtocolLimits() {
    }
}
