package io.github.kansamu216.vigil.client.agent;

/**
 * 客户端握手失败原因。
 *
 * <p>只用于客户端日志与状态机；不下发给服务端，避免把本地信任配置暴露给对端。</p>
 */
public class ClientHandshakeException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** 预置指纹与服务端公钥或挑战声明不一致。 */
        UNTRUSTED_SERVER,
        /** 未配置信任锚，客户端拒绝自动接受任何公钥。 */
        NO_TRUST_ANCHOR,
        /** 公钥编码非法。 */
        INVALID_KEY,
        /** 挑战签名无效。 */
        SIGNATURE_INVALID,
        /** 挑战已过期。 */
        EXPIRED,
        /** 尚未验签就尝试应答。 */
        NOT_VERIFIED,
        /** 报告与挑战不匹配。 */
        REPORT_MISMATCH
    }

    private final Reason reason;

    public ClientHandshakeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ClientHandshakeException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
