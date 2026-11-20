package io.github.kansamu216.vigil.protocol.payload;

import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.WireReader;
import io.github.kansamu216.vigil.protocol.WireWriter;

/**
 * 截图分片（客户端 → 服务端）。
 *
 * <p>分片按 index 升序上传，每片都携带 totalChunks，服务端据此拒绝"先声明 3 片、
 * 后面再补第 4 片"这类越界重排。分片本身不被信任：整图必须与
 * {@link ScreenDecisionPayload} 声明的长度和 SHA-256 一致才会被接受。</p>
 */
public final class ScreenChunkPayload {

    /** 当前 schema。 */
    public static final int SCHEMA = 1;

    public static final int MAX_REQUEST_ID_CHARS = 64;

    private ScreenChunkPayload() {
    }

    public static byte[] encode(String requestId, int index, int totalChunks, byte[] data) {
        checkRequestId(requestId);
        if (totalChunks <= 0 || totalChunks > ProtocolLimits.MAX_SCREEN_CHUNKS) {
            throw new IllegalArgumentException("totalChunks out of range: " + totalChunks);
        }
        if (index < 0 || index >= totalChunks) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("chunk data must not be empty");
        }
        if (data.length > ProtocolLimits.MAX_SCREEN_CHUNK_BYTES) {
            throw new IllegalArgumentException("chunk too large: " + data.length);
        }
        WireWriter writer = new WireWriter(64 + data.length, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.u16(SCHEMA);
        writer.string(requestId);
        writer.u16(index);
        writer.u16(totalChunks);
        writer.bytes(data);
        return writer.toByteArray();
    }

    public static Decoded decode(byte[] payload) throws ProtocolException {
        WireReader reader = new WireReader(payload);
        int schema = reader.u16();
        if (schema != SCHEMA) {
            throw new ProtocolException(
                    ProtocolException.Code.UNSUPPORTED_VERSION, "screen chunk schema: " + schema);
        }
        String requestId = reader.string();
        int index = reader.u16();
        int totalChunks = reader.u16();
        byte[] data = reader.bytes();
        if (reader.remaining() != 0) {
            throw new ProtocolException(
                    ProtocolException.Code.LENGTH_MISMATCH,
                    "trailing bytes in screen chunk payload");
        }
        if (requestId.isEmpty() || requestId.length() > MAX_REQUEST_ID_CHARS) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "screen chunk requestId");
        }
        if (totalChunks <= 0 || totalChunks > ProtocolLimits.MAX_SCREEN_CHUNKS) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "totalChunks: " + totalChunks);
        }
        if (index >= totalChunks) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE,
                    "index " + index + " of " + totalChunks);
        }
        if (data.length == 0 || data.length > ProtocolLimits.MAX_SCREEN_CHUNK_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.TOO_LARGE, "chunk length: " + data.length);
        }
        return new Decoded(requestId, index, totalChunks, data);
    }

    private static void checkRequestId(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("requestId must not be empty");
        }
        if (requestId.length() > MAX_REQUEST_ID_CHARS) {
            throw new IllegalArgumentException(
                    "requestId must be at most " + MAX_REQUEST_ID_CHARS + " characters");
        }
    }

    /** 解码结果。 */
    public static final class Decoded {
        private final String requestId;
        private final int index;
        private final int totalChunks;
        private final byte[] data;

        Decoded(String requestId, int index, int totalChunks, byte[] data) {
            this.requestId = requestId;
            this.index = index;
            this.totalChunks = totalChunks;
            this.data = data;
        }

        public String requestId() {
            return requestId;
        }

        public int index() {
            return index;
        }

        public int totalChunks() {
            return totalChunks;
        }

        public byte[] data() {
            return data.clone();
        }
    }
}
