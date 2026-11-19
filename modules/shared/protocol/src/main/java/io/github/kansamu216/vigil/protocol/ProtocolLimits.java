package io.github.kansamu216.vigil.protocol;

/**
 * 协议硬上限。
 *
 * <p>所有长度、列表、字符串和分片都必须受这里约束；新增字段时先在这里定上限，
 * 再写编解码，避免出现无界读取。</p>
 */
public final class ProtocolLimits {

    /** 信封固定头长度：version(2)+messageId(2)+direction(1)+sequence(8)+payloadLen(2)=15。 */
    public static final int ENVELOPE_HEADER_BYTES = 2 + 2 + 1 + 8 + 2;

    /** payloadLen 字段在帧内的起始偏移，测试与工具需要按常量取值而不是硬编码。 */
    public static final int PAYLOAD_LENGTH_OFFSET = 2 + 2 + 1 + 8;

    /** 单个 payload 上限，超出即拒绝，不尝试分片。 */
    public static final int MAX_PAYLOAD_BYTES = 32 * 1024;

    /** 单帧总长度上限。 */
    public static final int MAX_FRAME_BYTES = ENVELOPE_HEADER_BYTES + MAX_PAYLOAD_BYTES;

    /** 单个字符串字段上限（UTF-8 字节）。 */
    public static final int MAX_STRING_BYTES = 512;

    /** Mod 清单条目上限。 */
    public static final int MAX_MOD_ENTRIES = 2048;

    /** 单个观测条目上限。 */
    public static final int MAX_OBSERVATIONS = 256;

    /** 截图分片数量上限。 */
    public static final int MAX_SCREEN_CHUNKS = 512;

    /** 截图分片总字节上限。 */
    public static final int MAX_SCREEN_TOTAL_BYTES = 8 * 1024 * 1024;

    private ProtocolLimits() {
    }
}
