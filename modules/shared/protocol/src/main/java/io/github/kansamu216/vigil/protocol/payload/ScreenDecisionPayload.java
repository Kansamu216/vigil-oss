package io.github.kansamu216.vigil.protocol.payload;

import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.WireReader;
import io.github.kansamu216.vigil.protocol.WireWriter;

import java.util.Arrays;

/**
 * 截图决定（客户端 → 服务端）。
 *
 * <p>客户端在本地完成策略判定与用户同意后才发送本帧。同意时同时给出图像元数据，
 * 服务端据此为后续分片建立预期；拒绝时只带原因码，不带任何图像信息。</p>
 *
 * <p>本帧是观测级：客户端声明"我拒绝了"或"我采集了多大的一张图"，都不构成处置证据；
 * 服务端只按分片实际内容与摘要做一致性校验。</p>
 */
public final class ScreenDecisionPayload {

    /** 当前 schema。 */
    public static final int SCHEMA = 1;

    /** 图像格式。 */
    public static final int FORMAT_NONE = 0;
    public static final int FORMAT_PNG = 1;

    /** 同意。 */
    public static final int REASON_NONE = 0;
    /** 用户拒绝。 */
    public static final int REASON_DENIED_BY_USER = 1;
    /** 客户端策略关闭了采集。 */
    public static final int REASON_DISABLED = 2;
    /** 目标不是游戏窗口。 */
    public static final int REASON_NOT_GAME_WINDOW = 3;
    /** 尺寸超出请求上限。 */
    public static final int REASON_TOO_LARGE = 4;
    /** 采集或编码失败。 */
    public static final int REASON_CAPTURE_FAILED = 5;

    public static final int MAX_REQUEST_ID_CHARS = 64;

    private ScreenDecisionPayload() {
    }

    /** 同意上传：携带图像元数据与整图 SHA-256。 */
    public static byte[] encodeAccepted(
            String requestId,
            int width,
            int height,
            int format,
            int totalBytes,
            byte[] sha256) {
        checkRequestId(requestId);
        if (width <= 0 || width > ProtocolLimits.MAX_SCREEN_DIMENSION) {
            throw new IllegalArgumentException("width out of range: " + width);
        }
        if (height <= 0 || height > ProtocolLimits.MAX_SCREEN_DIMENSION) {
            throw new IllegalArgumentException("height out of range: " + height);
        }
        if (format != FORMAT_PNG) {
            throw new IllegalArgumentException("unsupported format: " + format);
        }
        if (totalBytes <= 0 || totalBytes > ProtocolLimits.MAX_SCREEN_TOTAL_BYTES) {
            throw new IllegalArgumentException("totalBytes out of range: " + totalBytes);
        }
        if (sha256 == null || sha256.length != 32) {
            throw new IllegalArgumentException("sha256 must be 32 bytes");
        }
        return write(requestId, true, REASON_NONE, width, height, format, totalBytes, sha256);
    }

    /** 拒绝上传：只带原因码，元数据全为零。 */
    public static byte[] encodeRejected(String requestId, int reasonCode) {
        checkRequestId(requestId);
        if (reasonCode < REASON_DENIED_BY_USER || reasonCode > REASON_CAPTURE_FAILED) {
            throw new IllegalArgumentException("reasonCode out of range: " + reasonCode);
        }
        return write(requestId, false, reasonCode, 0, 0, FORMAT_NONE, 0, new byte[0]);
    }

    private static byte[] write(
            String requestId,
            boolean accepted,
            int reasonCode,
            int width,
            int height,
            int format,
            int totalBytes,
            byte[] sha256) {
        WireWriter writer = new WireWriter(96, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.u16(SCHEMA);
        writer.string(requestId);
        writer.u8(accepted ? 1 : 0);
        writer.u8(reasonCode);
        writer.u16(width);
        writer.u16(height);
        writer.u8(format);
        writer.i32(totalBytes);
        writer.bytes(sha256);
        return writer.toByteArray();
    }

    public static Decoded decode(byte[] payload) throws ProtocolException {
        WireReader reader = new WireReader(payload);
        int schema = reader.u16();
        if (schema != SCHEMA) {
            throw new ProtocolException(
                    ProtocolException.Code.UNSUPPORTED_VERSION, "screen decision schema: " + schema);
        }
        String requestId = reader.string();
        int acceptedFlag = reader.u8();
        int reasonCode = reader.u8();
        int width = reader.u16();
        int height = reader.u16();
        int format = reader.u8();
        int totalBytes = reader.i32();
        byte[] sha256 = reader.bytes();
        if (reader.remaining() != 0) {
            throw new ProtocolException(
                    ProtocolException.Code.LENGTH_MISMATCH,
                    "trailing bytes in screen decision payload");
        }
        if (requestId.isEmpty() || requestId.length() > MAX_REQUEST_ID_CHARS) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "screen decision requestId");
        }
        if (acceptedFlag != 0 && acceptedFlag != 1) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "accepted flag: " + acceptedFlag);
        }
        boolean accepted = acceptedFlag == 1;
        if (accepted) {
            if (reasonCode != REASON_NONE) {
                throw new ProtocolException(
                        ProtocolException.Code.FIELD_OUT_OF_RANGE,
                        "accepted decision with reason " + reasonCode);
            }
            if (width <= 0 || width > ProtocolLimits.MAX_SCREEN_DIMENSION
                    || height <= 0 || height > ProtocolLimits.MAX_SCREEN_DIMENSION) {
                throw new ProtocolException(
                        ProtocolException.Code.FIELD_OUT_OF_RANGE,
                        "accepted decision with size " + width + "x" + height);
            }
            if (format != FORMAT_PNG) {
                throw new ProtocolException(
                        ProtocolException.Code.FIELD_OUT_OF_RANGE, "format: " + format);
            }
            if (totalBytes <= 0 || totalBytes > ProtocolLimits.MAX_SCREEN_TOTAL_BYTES) {
                throw new ProtocolException(
                        ProtocolException.Code.FIELD_OUT_OF_RANGE, "totalBytes: " + totalBytes);
            }
            if (sha256.length != 32) {
                throw new ProtocolException(
                        ProtocolException.Code.FIELD_OUT_OF_RANGE,
                        "digest length: " + sha256.length);
            }
        } else {
            if (reasonCode < REASON_DENIED_BY_USER || reasonCode > REASON_CAPTURE_FAILED) {
                throw new ProtocolException(
                        ProtocolException.Code.FIELD_OUT_OF_RANGE, "reasonCode: " + reasonCode);
            }
            if (width != 0 || height != 0 || format != FORMAT_NONE || totalBytes != 0
                    || sha256.length != 0) {
                throw new ProtocolException(
                        ProtocolException.Code.FIELD_OUT_OF_RANGE,
                        "rejected decision must not carry image metadata");
            }
        }
        return new Decoded(requestId, accepted, reasonCode, width, height, format, totalBytes, sha256);
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
        private final boolean accepted;
        private final int reasonCode;
        private final int width;
        private final int height;
        private final int format;
        private final int totalBytes;
        private final byte[] sha256;

        Decoded(
                String requestId,
                boolean accepted,
                int reasonCode,
                int width,
                int height,
                int format,
                int totalBytes,
                byte[] sha256) {
            this.requestId = requestId;
            this.accepted = accepted;
            this.reasonCode = reasonCode;
            this.width = width;
            this.height = height;
            this.format = format;
            this.totalBytes = totalBytes;
            this.sha256 = sha256;
        }

        public String requestId() {
            return requestId;
        }

        public boolean accepted() {
            return accepted;
        }

        public int reasonCode() {
            return reasonCode;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int format() {
            return format;
        }

        public int totalBytes() {
            return totalBytes;
        }

        public byte[] sha256() {
            return sha256.clone();
        }

        @Override
        public String toString() {
            return "Decoded{requestId=" + requestId + ", accepted=" + accepted
                    + ", reasonCode=" + reasonCode + ", size=" + width + "x" + height
                    + ", totalBytes=" + totalBytes + ", sha256=" + Arrays.toString(sha256) + '}';
        }
    }
}
