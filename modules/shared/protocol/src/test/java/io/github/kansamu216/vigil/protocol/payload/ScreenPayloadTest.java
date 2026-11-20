package io.github.kansamu216.vigil.protocol.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import org.junit.jupiter.api.Test;

/**
 * 截图三帧的编解码与边界用例。
 */
class ScreenPayloadTest {

    private static byte[] digest() {
        byte[] value = new byte[32];
        for (int i = 0; i < value.length; i++) {
            value[i] = (byte) i;
        }
        return value;
    }

    @Test
    void roundTripsRequest() throws ProtocolException {
        byte[] encoded = ScreenRequestPayload.encode(
                "screenshot-1", "manual", ScreenRequestPayload.SCOPE_FULL_SCREEN,
                2_000L, 1_048_576, 64, 16_384);
        ScreenRequestPayload.Decoded decoded = ScreenRequestPayload.decode(encoded);
        assertEquals("screenshot-1", decoded.requestId());
        assertEquals("manual", decoded.reason());
        assertEquals(ScreenRequestPayload.SCOPE_FULL_SCREEN, decoded.scope());
        assertTrue(decoded.fullScreen());
        assertEquals(2_000L, decoded.expiresAtMillis());
        assertEquals(1_048_576, decoded.maxTotalBytes());
        assertEquals(64, decoded.maxChunks());
        assertEquals(16_384, decoded.maxChunkBytes());
    }

    @Test
    void requestAcceptsEmptyReason() throws ProtocolException {
        byte[] encoded = ScreenRequestPayload.encode(
                "screenshot-2", null, ScreenRequestPayload.SCOPE_GAME_WINDOW, 10L, 1024, 1, 1024);
        assertEquals("", ScreenRequestPayload.decode(encoded).reason());
    }

    @Test
    void requestRejectsOutOfRangeLimits() {
        int scope = ScreenRequestPayload.SCOPE_GAME_WINDOW;
        assertThrows(IllegalArgumentException.class, () -> ScreenRequestPayload.encode(
                "screenshot-3", "r", scope, 10L, 0, 1, 1024));
        assertThrows(IllegalArgumentException.class, () -> ScreenRequestPayload.encode(
                "screenshot-3", "r", scope, 10L, 1024, 0, 1024));
        assertThrows(IllegalArgumentException.class, () -> ScreenRequestPayload.encode(
                "screenshot-3", "r", scope, 10L, 1024, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ScreenRequestPayload.encode(
                "screenshot-3", "r", scope, 10L,
                ProtocolLimits.MAX_SCREEN_TOTAL_BYTES + 1, 1, 1024));
        assertThrows(IllegalArgumentException.class, () -> ScreenRequestPayload.encode(
                "", "r", scope, 10L, 1024, 1, 1024));
        assertThrows(IllegalArgumentException.class, () -> ScreenRequestPayload.encode(
                "screenshot-3", "r", 9, 10L, 1024, 1, 1024));
    }

    @Test
    void requestRejectsTrailingBytes() {
        byte[] encoded = ScreenRequestPayload.encode(
                "screenshot-4", "r", ScreenRequestPayload.SCOPE_GAME_WINDOW, 10L, 1024, 1, 1024);
        byte[] longer = new byte[encoded.length + 1];
        System.arraycopy(encoded, 0, longer, 0, encoded.length);
        assertThrows(ProtocolException.class, () -> ScreenRequestPayload.decode(longer));
    }

    @Test
    void roundTripsAcceptedDecision() throws ProtocolException {
        byte[] encoded = ScreenDecisionPayload.encodeAccepted(
                "screenshot-5", 1920, 1080, ScreenDecisionPayload.FORMAT_PNG, 65_536, digest());
        ScreenDecisionPayload.Decoded decoded = ScreenDecisionPayload.decode(encoded);
        assertTrue(decoded.accepted());
        assertEquals(ScreenDecisionPayload.REASON_NONE, decoded.reasonCode());
        assertEquals(1920, decoded.width());
        assertEquals(1080, decoded.height());
        assertEquals(65_536, decoded.totalBytes());
        assertArrayEquals(digest(), decoded.sha256());
    }

    @Test
    void roundTripsRejectedDecision() throws ProtocolException {
        byte[] encoded = ScreenDecisionPayload.encodeRejected(
                "screenshot-6", ScreenDecisionPayload.REASON_DENIED_BY_USER);
        ScreenDecisionPayload.Decoded decoded = ScreenDecisionPayload.decode(encoded);
        assertFalse(decoded.accepted());
        assertEquals(ScreenDecisionPayload.REASON_DENIED_BY_USER, decoded.reasonCode());
        assertEquals(0, decoded.width());
        assertEquals(0, decoded.totalBytes());
        assertEquals(0, decoded.sha256().length);
    }

    @Test
    void decisionRejectsInconsistentMetadata() {
        assertThrows(IllegalArgumentException.class, () -> ScreenDecisionPayload.encodeAccepted(
                "screenshot-7", 0, 1080, ScreenDecisionPayload.FORMAT_PNG, 10, digest()));
        assertThrows(IllegalArgumentException.class, () -> ScreenDecisionPayload.encodeAccepted(
                "screenshot-7", 1920, 1080, ScreenDecisionPayload.FORMAT_PNG, 10, new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> ScreenDecisionPayload.encodeAccepted(
                "screenshot-7", 1920, 1080, ScreenDecisionPayload.FORMAT_NONE, 10, digest()));
        assertThrows(IllegalArgumentException.class, () -> ScreenDecisionPayload.encodeRejected(
                "screenshot-7", 9));
    }

    @Test
    void decisionRejectsRejectedFrameCarryingMetadata() {
        // 手工构造：accepted=0 但带有图像字段，解码器必须拒绝而不是忽略。
        io.github.kansamu216.vigil.protocol.WireWriter writer =
                new io.github.kansamu216.vigil.protocol.WireWriter(
                        96, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.u16(ScreenDecisionPayload.SCHEMA);
        writer.string("screenshot-8");
        writer.u8(0);
        writer.u8(ScreenDecisionPayload.REASON_DENIED_BY_USER);
        writer.u16(100);
        writer.u16(100);
        writer.u8(ScreenDecisionPayload.FORMAT_PNG);
        writer.i32(1024);
        writer.bytes(digest());
        assertThrows(ProtocolException.class,
                () -> ScreenDecisionPayload.decode(writer.toByteArray()));
    }

    @Test
    void roundTripsChunk() throws ProtocolException {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }
        byte[] encoded = ScreenChunkPayload.encode("screenshot-9", 2, 5, data);
        ScreenChunkPayload.Decoded decoded = ScreenChunkPayload.decode(encoded);
        assertEquals("screenshot-9", decoded.requestId());
        assertEquals(2, decoded.index());
        assertEquals(5, decoded.totalChunks());
        assertArrayEquals(data, decoded.data());
    }

    @Test
    void chunkRejectsOutOfRangeIndexAndSize() {
        byte[] data = new byte[] {1};
        assertThrows(IllegalArgumentException.class,
                () -> ScreenChunkPayload.encode("screenshot-10", 5, 5, data));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenChunkPayload.encode("screenshot-10", -1, 5, data));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenChunkPayload.encode("screenshot-10", 0, 0, data));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenChunkPayload.encode("screenshot-10", 0, 1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> ScreenChunkPayload.encode(
                "screenshot-10", 0, 1,
                new byte[ProtocolLimits.MAX_SCREEN_CHUNK_BYTES + 1]));
    }

    @Test
    void chunkRejectsTooManyTotalChunks() {
        // 手工构造超过总片数上限的声明，模拟对端谎报片数。
        io.github.kansamu216.vigil.protocol.WireWriter writer =
                new io.github.kansamu216.vigil.protocol.WireWriter(
                        96, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.u16(ScreenChunkPayload.SCHEMA);
        writer.string("screenshot-11");
        writer.u16(0);
        writer.u16(ProtocolLimits.MAX_SCREEN_CHUNKS + 1);
        writer.bytes(new byte[] {1, 2, 3});
        assertThrows(ProtocolException.class,
                () -> ScreenChunkPayload.decode(writer.toByteArray()));
    }

    @Test
    void chunkRejectsTruncatedPayload() {
        byte[] encoded = ScreenChunkPayload.encode("screenshot-12", 0, 1, new byte[] {1, 2, 3});
        byte[] shorter = new byte[encoded.length - 2];
        System.arraycopy(encoded, 0, shorter, 0, shorter.length);
        assertThrows(ProtocolException.class, () -> ScreenChunkPayload.decode(shorter));
    }
}
