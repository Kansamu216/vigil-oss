package io.github.kansamu216.vigil.crypto;

public class CryptoException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {

        AUTHENTICATION_FAILED,

        INVALID_INPUT,

        KEY_UNWRAP_FAILED,

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
