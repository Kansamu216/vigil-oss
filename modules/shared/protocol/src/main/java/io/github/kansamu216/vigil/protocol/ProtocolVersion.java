package io.github.kansamu216.vigil.protocol;

/**
 * 协议版本。major 不兼容、minor 向后兼容，两者独立演进。
 */
public final class ProtocolVersion {

    /** 当前实现的 major。跨 major 必须走兼容/降级模式，不得静默按旧语义解析。 */
    public static final int MAJOR = 1;

    /** 当前实现的 minor。新增可选字段时递增。 */
    public static final int MINOR = 0;

    private ProtocolVersion() {
    }

    /** 线上只传 major；minor 通过配置与审计记录，避免每帧多两字节。 */
    public static boolean isSupported(int major) {
        return major == MAJOR;
    }

    public static String display() {
        return MAJOR + "." + MINOR;
    }
}
