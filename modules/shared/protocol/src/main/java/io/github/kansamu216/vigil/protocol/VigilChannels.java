package io.github.kansamu216.vigil.protocol;

/**
 * 通道标识与协议版本常量。
 *
 * <p>值通过方法暴露，避免编译期内联造成的版本偏差。</p>
 */
public final class VigilChannels {

    /** 通道命名空间，与 modId 一致。 */
    private static final String NAMESPACE = "vigil";

    /** 连通性探测消息 id。 */
    private static final String PING = "ping";

    /** 会话帧承载通道 id，负载是已编码的协议信封。 */
    private static final String FRAME = "frame";

    /**
     * 通道注册版本，用于核对两端注册表。
     */
    private static final int PROTOCOL_VERSION = 1;

    private VigilChannels() {
    }

    public static String namespace() {
        return NAMESPACE;
    }

    public static String pingId() {
        return PING;
    }

    public static String frameId() {
        return FRAME;
    }

    public static int protocolVersion() {
        return PROTOCOL_VERSION;
    }
}
