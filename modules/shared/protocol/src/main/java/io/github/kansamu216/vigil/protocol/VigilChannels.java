package io.github.kansamu216.vigil.protocol;

/**
 * 通道标识基线。
 *
 * <p>这里是命名空间、消息 id 和协议版本的唯一事实源：平台适配层不得各写一份常量，
 * 否则改名时会出现“服务端认得、客户端不认得”的半兼容状态。</p>
 *
 * <p>值通过方法暴露而不是让调用方直接读常量字段：常量字段会被编译器内联进调用方，
 * 客户端重定位时就无法改写引用，也无法在不重新编译全部适配层的前提下调整取值。</p>
 *
 * <p>只使用 Java 8 API，供 1.20.x/1.21.x 以及未来 adapter 共用。</p>
 */
public final class VigilChannels {

    /** 通道命名空间，与 modId 一致；已发布后改名等于协议不兼容。 */
    private static final String NAMESPACE = "vigil";

    /** 连通性探测消息 id。 */
    private static final String PING = "ping";

    /** 会话帧承载通道 id，负载是已编码的协议信封。 */
    private static final String FRAME = "frame";

    /**
     * 通道注册版本。M1 定义 wire 协议时由这里起算，
     * 当前值只表示“两端注册表一致”，不代表已有握手语义。
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
