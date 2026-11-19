package io.github.kansamu216.vigil.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 信封编解码的正常与畸形用例。
 */
class EnvelopeCodecTest {

    private static byte[] frameOf(int version, int messageId, int direction, long sequence, byte[] payload) {
        WireWriter writer = new WireWriter(64, ProtocolLimits.MAX_FRAME_BYTES);
        writer.u16(version);
        writer.u16(messageId);
        writer.u8(direction);
        writer.i64(sequence);
        writer.u16(payload.length);
        writer.raw(payload);
        return writer.toByteArray();
    }

    @Test
    void roundTripsPayload() throws ProtocolException {
        Envelope original = Envelope.of(
                MessageType.AUTH, Direction.CLIENT_TO_SERVER, 42L, new byte[] {1, 2, 3});
        Envelope decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(original));
        assertEquals(original, decoded);
        assertEquals(42L, decoded.sequence());
        assertArrayEquals(new byte[] {1, 2, 3}, decoded.payload());
    }

    @Test
    void acceptsEmptyPayload() throws ProtocolException {
        Envelope decoded =
                EnvelopeCodec.decode(EnvelopeCodec.encode(
                        Envelope.of(MessageType.KEEPALIVE, Direction.SERVER_TO_CLIENT, 0L, new byte[0])));
        assertEquals(0, decoded.payloadLength());
    }

    @Test
    void rejectsTruncatedHeader() {
        ProtocolException error = assertThrows(
                ProtocolException.class, () -> EnvelopeCodec.decode(new byte[] {0, 1, 0, 1, 0}));
        assertEquals(ProtocolException.Code.TRUNCATED, error.code());
    }

    @Test
    void rejectsDeclaredLengthBeyondAvailableBytes() {
        byte[] frame = frameOf(1, MessageType.AUTH.id(), 0, 1L, new byte[] {1, 2, 3});
        // 声明 3 字节但只保留 2 字节。
        byte[] truncated = new byte[frame.length - 1];
        System.arraycopy(frame, 0, truncated, 0, truncated.length);
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(truncated));
        assertEquals(ProtocolException.Code.LENGTH_MISMATCH, error.code());
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] frame = frameOf(1, MessageType.AUTH.id(), 0, 1L, new byte[] {1});
        byte[] withTail = new byte[frame.length + 1];
        System.arraycopy(frame, 0, withTail, 0, frame.length);
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(withTail));
        assertEquals(ProtocolException.Code.LENGTH_MISMATCH, error.code());
    }

    @Test
    void rejectsUnknownMessageId() {
        byte[] frame = frameOf(1, 0x7FFF, 0, 1L, new byte[0]);
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(frame));
        assertEquals(ProtocolException.Code.UNKNOWN_MESSAGE, error.code());
    }

    @Test
    void rejectsInvalidDirection() {
        byte[] frame = frameOf(1, MessageType.AUTH.id(), 7, 1L, new byte[0]);
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(frame));
        assertEquals(ProtocolException.Code.INVALID_DIRECTION, error.code());
    }

    @Test
    void rejectsUnsupportedVersion() {
        byte[] frame = frameOf(2, MessageType.AUTH.id(), 0, 1L, new byte[0]);
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(frame));
        assertEquals(ProtocolException.Code.UNSUPPORTED_VERSION, error.code());
    }

    @Test
    void rejectsNegativeSequence() {
        byte[] frame = frameOf(1, MessageType.AUTH.id(), 0, -1L, new byte[0]);
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(frame));
        assertEquals(ProtocolException.Code.FIELD_OUT_OF_RANGE, error.code());
    }

    @Test
    void rejectsOversizedPayload() {
        byte[] frame = frameOf(1, MessageType.AUTH.id(), 0, 1L, new byte[0]);
        // 把 payloadLen 改成超过上限的值。
        frame[ProtocolLimits.PAYLOAD_LENGTH_OFFSET] = (byte) 0xFF;
        frame[ProtocolLimits.PAYLOAD_LENGTH_OFFSET + 1] = (byte) 0xFF;
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(frame));
        assertEquals(ProtocolException.Code.TOO_LARGE, error.code());
    }

    @Test
    void rejectsOversizedFrameBeforeParsing() {
        byte[] huge = new byte[ProtocolLimits.MAX_FRAME_BYTES + 1];
        ProtocolException error =
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(huge));
        assertEquals(ProtocolException.Code.TOO_LARGE, error.code());
    }
}
