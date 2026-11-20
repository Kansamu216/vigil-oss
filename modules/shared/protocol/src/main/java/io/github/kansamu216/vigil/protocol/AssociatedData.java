package io.github.kansamu216.vigil.protocol;

public final class AssociatedData {

    private AssociatedData() {
    }

    public static byte[] build(
            int protocolVersion, byte[] sessionId, int messageId, Direction direction, long sequence) {

        if (sessionId.length == 0 || sessionId.length > 64) {
            throw new IllegalArgumentException("sessionId length out of range: " + sessionId.length);
        }
        WireWriter writer = new WireWriter(64, 128);
        writer.u16(protocolVersion);
        writer.bytes(sessionId);
        writer.u16(messageId);
        writer.u8(direction.wireValue());
        writer.i64(sequence);
        return writer.toByteArray();
    }

    public static byte[] build(int protocolVersion, byte[] sessionId, Envelope envelope) {
        return build(
                protocolVersion,
                sessionId,
                envelope.type().id(),
                envelope.direction(),
                envelope.sequence());
    }
}
