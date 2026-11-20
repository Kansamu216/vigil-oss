package io.github.kansamu216.vigil.protocol;

public class ProtocolException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Code {

        TRUNCATED,

        TOO_LARGE,

        UNSUPPORTED_VERSION,

        UNKNOWN_MESSAGE,

        INVALID_DIRECTION,

        LENGTH_MISMATCH,

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
