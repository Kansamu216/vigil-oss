package io.github.kansamu216.vigil.protocol;

/**
 * 协议层失败，带可审计的错误码。
 *
 * <p>错误码用于日志与指标，不携带密钥材料。
 */
public class ProtocolException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Code {
        /** 帧长度小于最小信封长度或字段被截断。 */
        TRUNCATED,
        /** 帧长度超过硬上限。 */
        TOO_LARGE,
        /** 协议版本不受支持。 */
        UNSUPPORTED_VERSION,
        /** 未知消息 id。 */
        UNKNOWN_MESSAGE,
        /** 方向字段非法。 */
        INVALID_DIRECTION,
        /** payload 长度与声明不一致（多余尾部或不足）。 */
        LENGTH_MISMATCH,
        /** 字段值超出允许范围（负数、超长字符串等）。 */
        FIELD_OUT_OF_RANGE
    }

    private final Code code;

    public ProtocolException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
