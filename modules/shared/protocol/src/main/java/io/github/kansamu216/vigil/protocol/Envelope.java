package io.github.kansamu216.vigil.protocol;

import java.util.Arrays;

public final class Envelope {

    private final int version;
    private final MessageType type;
    private final Direction direction;
    private final long sequence;
    private final byte[] payload;

    public Envelope(int version, MessageType type, Direction direction, long sequence, byte[] payload) {
        if (payload.length > ProtocolLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload too large: " + payload.length);
        }
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        this.version = version;
        this.type = type;
        this.direction = direction;
        this.sequence = sequence;
        this.payload = payload.clone();
    }

    public static Envelope of(MessageType type, Direction direction, long sequence, byte[] payload) {
        return new Envelope(ProtocolVersion.MAJOR, type, direction, sequence, payload);
    }

    public int version() {
        return version;
    }

    public MessageType type() {
        return type;
    }

    public Direction direction() {
        return direction;
    }

    public long sequence() {
        return sequence;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Envelope)) {
            return false;
        }
        Envelope that = (Envelope) other;
        return version == that.version
                && type == that.type
                && direction == that.direction
                && sequence == that.sequence
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = version;
        result = 31 * result + type.hashCode();
        result = 31 * result + direction.hashCode();
        result = 31 * result + (int) (sequence ^ (sequence >>> 32));
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {

        return "Envelope{version=" + version + ", type=" + type
                + ", direction=" + direction + ", sequence=" + sequence
                + ", payloadLen=" + payload.length + "}";
    }
}
