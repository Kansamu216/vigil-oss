package io.github.kansamu216.vigil.crypto;

/**
 * 加密相关失败。
 *
 * <p>对外只暴露失败类别，不把底层异常文本或密钥材料带出去。</p>
 */
public class CryptoException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** 认证失败：密文被篡改、AAD 不匹配或密钥不对。 */
        AUTHENTICATION_FAILED,
        /** 输入长度或参数非法。 */
        INVALID_INPUT,
        /** 密钥包裹失败（RSA-OAEP 解密失败通常意味着跨交付）。 */
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
