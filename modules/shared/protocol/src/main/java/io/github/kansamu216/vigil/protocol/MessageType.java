package io.github.kansamu216.vigil.protocol;

/**
 * 消息类型。
 *
 * <p>id 发布后不复用；新消息分配唯一 id。</p>
 */
public enum MessageType {

    /** 服务端下发签名挑战（含 nonce、公钥指纹、有效期）。 */
    CHALLENGE(0x0001),

    /** 客户端提交会话密钥包裹与加密报告。 */
    AUTH(0x0002),

    /** 会话保活。 */
    KEEPALIVE(0x0003),

    /** 连通性探测。 */
    PING(0x0004),

    /** 服务端下发判定结果（仅告知，不含处置凭据）。 */
    VERDICT(0x0005),

    /** 截图请求（分片传输的控制消息）。 */
    SCREEN_REQUEST(0x0101),

    /** 截图分片。 */
    SCREEN_CHUNK(0x0102);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MessageType fromId(int id) throws ProtocolException {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new ProtocolException(
                ProtocolException.Code.UNKNOWN_MESSAGE, "unknown message id: " + id);
    }
}
