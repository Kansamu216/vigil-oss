package io.github.kansamu216.vigil.protocol;

/**
 * AEAD 附加认证数据（AAD）。
 *
 * <p>绑定 version、sessionId、messageId、direction、sequence 五元组，
 * 使密文无法跨会话、跨方向、跨消息类型或跨序号重放。</p>
 */
public final class AssociatedData {

    private AssociatedData() {
    }

    /**
     * 生成 AAD 字节。与线上信封字段同序编码，便于人工比对抓包。
     */
    public static byte[] build(
            int protocolVersion, byte[] sessionId, int messageId, Direction direction, long sequence) {
        // UUID 字符串为 36 字节，这里放宽到 64 以容纳未来的会话 id 形式。
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
