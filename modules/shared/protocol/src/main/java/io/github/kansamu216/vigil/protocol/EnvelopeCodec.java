package io.github.kansamu216.vigil.protocol;

public final class EnvelopeCodec {

    private EnvelopeCodec() {
    }

    public static byte[] encode(Envelope envelope) {
        if (envelope.payloadLength() > ProtocolLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload too large: " + envelope.payloadLength());
        }
        WireWriter writer = new WireWriter(
                ProtocolLimits.ENVELOPE_HEADER_BYTES + envelope.payloadLength(),
                ProtocolLimits.MAX_FRAME_BYTES);
        writer.u16(envelope.version());
        writer.u16(envelope.type().id());
        writer.u8(envelope.direction().wireValue());
        writer.i64(envelope.sequence());
        writer.u16(envelope.payloadLength());
        writer.raw(envelope.payload());
        return writer.toByteArray();
    }

    public static Envelope decode(byte[] frame) throws ProtocolException {
        if (frame == null) {
            throw new ProtocolException(ProtocolException.Code.TRUNCATED, "null frame");
        }
        return decode(frame, 0, frame.length);
    }

    public static Envelope decode(byte[] frame, int offset, int length) throws ProtocolException {
        if (frame == null) {
            throw new ProtocolException(ProtocolException.Code.TRUNCATED, "null frame");
        }
        if (offset < 0 || length < 0 || offset + length > frame.length) {
            throw new ProtocolException(ProtocolException.Code.TRUNCATED, "invalid slice");
        }
        if (length > ProtocolLimits.MAX_FRAME_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.TOO_LARGE, "frame too large: " + length);
        }
        if (length < ProtocolLimits.ENVELOPE_HEADER_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.TRUNCATED, "frame shorter than header: " + length);
        }

        WireReader reader = new WireReader(frame, offset, length);
        int version = reader.u16();
        if (!ProtocolVersion.isSupported(version)) {
            throw new ProtocolException(
                    ProtocolException.Code.UNSUPPORTED_VERSION, "protocol version: " + version);
        }
        int messageId = reader.u16();
        MessageType type = MessageType.fromId(messageId);
        Direction direction = Direction.fromWireValue(reader.u8());
        long sequence = reader.i64();
        if (sequence < 0L) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "negative sequence: " + sequence);
        }
        int payloadLength = reader.u16();
        if (payloadLength > ProtocolLimits.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.TOO_LARGE, "payload too large: " + payloadLength);
        }
        if (payloadLength != reader.remaining()) {
            throw new ProtocolException(
                    ProtocolException.Code.LENGTH_MISMATCH,
                    "payload length " + payloadLength + " but " + reader.remaining() + " bytes left");
        }
        byte[] payload = reader.raw(payloadLength);
        return new Envelope(version, type, direction, sequence, payload);
    }
}
