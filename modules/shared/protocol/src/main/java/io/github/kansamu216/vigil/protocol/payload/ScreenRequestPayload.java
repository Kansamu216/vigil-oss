package io.github.kansamu216.vigil.protocol.payload;

import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.WireReader;
import io.github.kansamu216.vigil.protocol.WireWriter;

/**
 * 截图请求（服务端 → 客户端）。
 *
 * <p>请求携带本次采集的全部上限，客户端必须在这些限制内完成分片。
 * 服务端下发时同时下发过期时刻；过期后客户端即使已完成采集也不得上传。</p>
 */
public final class ScreenRequestPayload {

    /** 当前 schema。字段顺序固定，新增字段必须提升 schema 并保留旧分支。 */
    public static final int SCHEMA = 1;

    public static final int MAX_REQUEST_ID_CHARS = 64;
    public static final int MAX_REASON_CHARS = 256;

    /** 采集范围：仅游戏窗口。 */
    public static final int SCOPE_GAME_WINDOW = 1;
    /** 采集范围：整个屏幕。仅私有部署使用，且必须与告知文本一致。 */
    public static final int SCOPE_FULL_SCREEN = 2;

    private ScreenRequestPayload() {
    }

    public static byte[] encode(
            String requestId,
            String reason,
            int scope,
            long expiresAtMillis,
            int maxTotalBytes,
            int maxChunks,
            int maxChunkBytes) {
        String id = checkRequestId(requestId);
        String text = checkReason(reason);
        checkScope(scope);
        if (expiresAtMillis <= 0L) {
            throw new IllegalArgumentException("expiresAtMillis must be positive");
        }
        checkMaxTotalBytes(maxTotalBytes);
        checkMaxChunks(maxChunks);
        checkMaxChunkBytes(maxChunkBytes);

        WireWriter writer = new WireWriter(128, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.u16(SCHEMA);
        writer.string(id);
        writer.string(text);
        writer.u8(scope);
        writer.i64(expiresAtMillis);
        writer.i32(maxTotalBytes);
        writer.u16(maxChunks);
        writer.u16(maxChunkBytes);
        return writer.toByteArray();
    }

    public static Decoded decode(byte[] payload) throws ProtocolException {
        WireReader reader = new WireReader(payload);
        int schema = reader.u16();
        if (schema != SCHEMA) {
            throw new ProtocolException(
                    ProtocolException.Code.UNSUPPORTED_VERSION, "screen request schema: " + schema);
        }
        String requestId = reader.string();
        String reason = reader.string();
        int scope = reader.u8();
        long expiresAtMillis = reader.i64();
        int maxTotalBytes = reader.i32();
        int maxChunks = reader.u16();
        int maxChunkBytes = reader.u16();
        if (reader.remaining() != 0) {
            throw new ProtocolException(
                    ProtocolException.Code.LENGTH_MISMATCH,
                    "trailing bytes in screen request payload");
        }
        if (requestId.length() > MAX_REQUEST_ID_CHARS) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE,
                    "screen request id too long: " + requestId.length());
        }
        if (scope != SCOPE_GAME_WINDOW && scope != SCOPE_FULL_SCREEN) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "screen scope: " + scope);
        }
        if (expiresAtMillis <= 0L) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "non-positive deadline");
        }
        checkDecodedLimits(maxTotalBytes, maxChunks, maxChunkBytes);
        return new Decoded(
                requestId, reason, scope, expiresAtMillis, maxTotalBytes, maxChunks, maxChunkBytes);
    }

    private static void checkScope(int scope) {
        if (scope != SCOPE_GAME_WINDOW && scope != SCOPE_FULL_SCREEN) {
            throw new IllegalArgumentException("scope out of range: " + scope);
        }
    }

    private static String checkRequestId(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("requestId must not be empty");
        }
        if (requestId.length() > MAX_REQUEST_ID_CHARS) {
            throw new IllegalArgumentException(
                    "requestId must be at most " + MAX_REQUEST_ID_CHARS + " characters");
        }
        return requestId;
    }

    private static String checkReason(String reason) {
        if (reason == null) {
            return "";
        }
        if (reason.length() > MAX_REASON_CHARS) {
            throw new IllegalArgumentException(
                    "reason must be at most " + MAX_REASON_CHARS + " characters");
        }
        return reason;
    }

    private static void checkMaxTotalBytes(int maxTotalBytes) {
        if (maxTotalBytes <= 0 || maxTotalBytes > ProtocolLimits.MAX_SCREEN_TOTAL_BYTES) {
            throw new IllegalArgumentException("maxTotalBytes out of range: " + maxTotalBytes);
        }
    }

    private static void checkMaxChunks(int maxChunks) {
        if (maxChunks <= 0 || maxChunks > ProtocolLimits.MAX_SCREEN_CHUNKS) {
            throw new IllegalArgumentException("maxChunks out of range: " + maxChunks);
        }
    }

    private static void checkMaxChunkBytes(int maxChunkBytes) {
        if (maxChunkBytes <= 0 || maxChunkBytes > ProtocolLimits.MAX_SCREEN_CHUNK_BYTES) {
            throw new IllegalArgumentException("maxChunkBytes out of range: " + maxChunkBytes);
        }
    }

    private static void checkDecodedLimits(int maxTotalBytes, int maxChunks, int maxChunkBytes)
            throws ProtocolException {
        if (maxTotalBytes <= 0 || maxTotalBytes > ProtocolLimits.MAX_SCREEN_TOTAL_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "maxTotalBytes: " + maxTotalBytes);
        }
        if (maxChunks <= 0 || maxChunks > ProtocolLimits.MAX_SCREEN_CHUNKS) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "maxChunks: " + maxChunks);
        }
        if (maxChunkBytes <= 0 || maxChunkBytes > ProtocolLimits.MAX_SCREEN_CHUNK_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "maxChunkBytes: " + maxChunkBytes);
        }
    }

    /** 解码结果。 */
    public static final class Decoded {
        private final String requestId;
        private final String reason;
        private final int scope;
        private final long expiresAtMillis;
        private final int maxTotalBytes;
        private final int maxChunks;
        private final int maxChunkBytes;

        Decoded(
                String requestId,
                String reason,
                int scope,
                long expiresAtMillis,
                int maxTotalBytes,
                int maxChunks,
                int maxChunkBytes) {
            this.requestId = requestId;
            this.reason = reason;
            this.scope = scope;
            this.expiresAtMillis = expiresAtMillis;
            this.maxTotalBytes = maxTotalBytes;
            this.maxChunks = maxChunks;
            this.maxChunkBytes = maxChunkBytes;
        }

        public String requestId() {
            return requestId;
        }

        public String reason() {
            return reason;
        }

        public int scope() {
            return scope;
        }

        public boolean fullScreen() {
            return scope == SCOPE_FULL_SCREEN;
        }

        public long expiresAtMillis() {
            return expiresAtMillis;
        }

        public int maxTotalBytes() {
            return maxTotalBytes;
        }

        public int maxChunks() {
            return maxChunks;
        }

        public int maxChunkBytes() {
            return maxChunkBytes;
        }
    }
}
