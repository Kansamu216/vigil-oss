package io.github.kansamu216.vigil.crypto;

/**
 * 加密相关失败。
 *
 * <p>只暴露失败类别，不携带密钥材料。</p>
 */
public class CryptoException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** 认证失败。 */
        AUTHENTICATION_FAILED,
        /** 输入长度或参数非法。 */
        INVALID_INPUT,
        /** 密钥包裹失败。 */
        KEY_UNWRAP_FAILED,
        /** 签名校验失败。 */
        SIGNATURE_INVALID
    }

    private final Reason reason;

    public CryptoException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CryptoException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
